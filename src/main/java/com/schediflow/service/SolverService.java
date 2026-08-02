package com.schediflow.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.SolverJob;
import com.schediflow.domain.SolverJobStatus;
import com.schediflow.domain.SolverMode;
import com.schediflow.domain.Timetable;
import com.schediflow.dto.request.SolverRunRequest;
import com.schediflow.dto.response.SolverJobResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.SolverJobRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.TenantContext;
import com.schediflow.service.solver.SolverProblemBuilder;
import com.schediflow.solver.model.TimetableSolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Async timetable solving (SCHED-03), job tracking (SCHED-04) and cancellation (SCHED-05).
 *
 * <p>{@code run} returns as soon as the job row exists; Timefold does the work on its own threads
 * and the callbacks below persist progress and results.</p>
 */
@Service
public class SolverService {

    private static final Logger log = LoggerFactory.getLogger(SolverService.class);
    private static final int MAX_TIMEOUT_SECONDS = 3600;

    private final SolverManager<TimetableSolution, Long> solverManager;
    private final SolverProblemBuilder problemBuilder;
    private final SolverJobRepository solverJobRepository;
    private final TimetableRepository timetableRepository;
    private final LessonRepository lessonRepository;
    private final SolverResultWriter resultWriter;

    public SolverService(
            SolverManager<TimetableSolution, Long> solverManager,
            SolverProblemBuilder problemBuilder,
            SolverJobRepository solverJobRepository,
            TimetableRepository timetableRepository,
            LessonRepository lessonRepository,
            SolverResultWriter resultWriter) {
        this.solverManager = solverManager;
        this.problemBuilder = problemBuilder;
        this.solverJobRepository = solverJobRepository;
        this.timetableRepository = timetableRepository;
        this.lessonRepository = lessonRepository;
        this.resultWriter = resultWriter;
    }

    /**
     * Starts a solve and returns immediately with the job id.
     *
     * @return the QUEUED job; 409 if the timetable already has one running
     */
    @Transactional
    public SolverJobResponse run(JwtPrincipal principal, SolverRunRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Timetable timetable = timetableRepository
                .findByIdAndTenantId(req.timetableId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable not found: " + req.timetableId()));

        List<SolverJob> active = solverJobRepository.findByTimetableIdAndStatusIn(
                timetable.getId(),
                List.of(SolverJobStatus.QUEUED.name(), SolverJobStatus.RUNNING.name()));
        if (!active.isEmpty()) {
            throw new ConflictException(
                    "A solver job is already running for this timetable (job " + active.get(0).getId() + ")");
        }

        SolverMode mode = parseMode(req.mode());
        int timeoutSeconds = resolveTimeout(mode, req.timeoutSeconds());

        SolverJob job = new SolverJob();
        job.setTenantId(tenantId);
        job.setTimetableId(timetable.getId());
        job.setStatus(SolverJobStatus.QUEUED.name());
        job.setMode(mode.name());
        job.setTimeoutSeconds(timeoutSeconds);
        job.setRequestedBy(principal == null ? null : principal.userId());
        SolverJob saved = solverJobRepository.save(job);

        TimetableSolution problem = problemBuilder.build(timetable);
        if (problem.getLessons().isEmpty()) {
            // Nothing to arrange — finish here rather than starting a solver that cannot move.
            // Written in this transaction, not via the writer, because the row is not yet visible
            // to anything running outside it.
            saved.setStatus(SolverJobStatus.COMPLETED.name());
            saved.setHardViolations(0);
            saved.setSoftScore(0);
            saved.setScoreBreakdown("No lessons to schedule");
            saved.setStartedAt(OffsetDateTime.now());
            saved.setCompletedAt(OffsetDateTime.now());
            return toResponse(solverJobRepository.save(saved));
        }

        saved.setStatus(SolverJobStatus.RUNNING.name());
        saved.setStartedAt(OffsetDateTime.now());
        SolverJob running = solverJobRepository.save(saved);

        // The solver's callbacks write from their own threads in their own transactions, so they
        // cannot see this job until we commit. Start solving only once that has happened.
        Long jobId = running.getId();
        Long timetableIdForSolve = timetable.getId();
        afterCommit(() -> startSolving(jobId, tenantId, timetableIdForSolve, problem, timeoutSeconds));
        return toResponse(running);
    }

    /** Runs the action after the current transaction commits, or immediately if there is none. */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void startSolving(
            Long jobId, Long tenantId, Long timetableId, TimetableSolution problem, int timeoutSeconds) {

        solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblem(problem)
                .withConfigOverride(new SolverConfigOverride<TimetableSolution>()
                        .withTerminationConfig(
                                new TerminationConfig().withSpentLimit(Duration.ofSeconds(timeoutSeconds))))
                .withBestSolutionConsumer(best -> resultWriter.recordProgress(jobId, best))
                .withFinalBestSolutionConsumer(
                        best -> resultWriter.recordCompletion(jobId, tenantId, timetableId, best))
                .withExceptionHandler((id, throwable) -> {
                    log.error("Solver job {} failed", id, throwable);
                    resultWriter.recordFailure(jobId, throwable);
                })
                .run();
    }

    @Transactional(readOnly = true)
    public SolverJobResponse getJob(Long jobId) {
        Long tenantId = TenantContext.getTenantId();
        return toResponse(solverJobRepository
                .findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Solver job not found: " + jobId)));
    }

    @Transactional(readOnly = true)
    public Page<SolverJobResponse> listJobs(Long timetableId, Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        Page<SolverJob> page = timetableId == null
                ? solverJobRepository.findByTenantIdOrderByIdDesc(tenantId, pageable)
                : solverJobRepository.findByTenantIdAndTimetableIdOrderByIdDesc(tenantId, timetableId, pageable);
        return page.map(SolverService::toResponse);
    }

    /**
     * Asks Timefold to stop early (SCHED-05). The best solution found so far has already been
     * written by the progress callback, so cancelling never loses work.
     */
    @Transactional
    public SolverJobResponse cancel(Long jobId) {
        Long tenantId = TenantContext.getTenantId();
        SolverJob job = solverJobRepository
                .findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Solver job not found: " + jobId));

        if (SolverJobStatus.valueOf(job.getStatus()).isTerminal()) {
            throw new BadRequestException(
                    "Solver job is already " + job.getStatus() + " and cannot be cancelled");
        }

        solverManager.terminateEarly(jobId);
        job.setStatus(SolverJobStatus.CANCELLED.name());
        job.setCompletedAt(OffsetDateTime.now());
        return toResponse(solverJobRepository.save(job));
    }

    static SolverMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return SolverMode.BALANCED;
        }
        String normalized = raw.trim().toUpperCase();
        return Arrays.stream(SolverMode.values())
                .filter(m -> m.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Invalid mode: " + raw + ". Must be one of: FAST, BALANCED, THOROUGH"));
    }

    private static int resolveTimeout(SolverMode mode, Integer override) {
        int seconds = override != null ? override : mode.defaultTimeoutSeconds();
        if (seconds > MAX_TIMEOUT_SECONDS) {
            throw new BadRequestException(
                    "timeoutSeconds must not exceed " + MAX_TIMEOUT_SECONDS);
        }
        return seconds;
    }

    static SolverJobResponse toResponse(SolverJob job) {
        String quality = job.getHardViolations() == null
                ? null
                : job.getHardViolations() + " hard / " + job.getSoftScore() + " soft";
        return new SolverJobResponse(
                job.getId(),
                job.getTimetableId(),
                job.getStatus(),
                job.getMode(),
                job.getTimeoutSeconds(),
                quality,
                job.getHardViolations(),
                job.getSoftScore(),
                job.getScoreBreakdown(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getCompletedAt());
    }
}
