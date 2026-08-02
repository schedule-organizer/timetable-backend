package com.schediflow.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.SolverJob;
import com.schediflow.domain.SolverJobStatus;
import com.schediflow.dto.event.LessonUpdatedEvent;
import com.schediflow.dto.event.SolverCompleteEvent;
import com.schediflow.dto.event.SolverProgressEvent;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.SolverJobRepository;
import com.schediflow.solver.model.PeriodSlot;
import com.schediflow.solver.model.TimetableSolution;
import com.schediflow.websocket.WebSocketDestinations;
import com.schediflow.websocket.WebSocketEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persists solver callbacks and publishes progress events (SCHED-03/04/05/06).
 *
 * <p>Separate from {@link SolverService} because Timefold invokes these from its own threads, which
 * carry no transaction and no {@code TenantContext}. Each method therefore opens its own
 * transaction ({@code REQUIRES_NEW}) and takes every id it needs explicitly.</p>
 */
@Component
public class SolverResultWriter {

    private static final Logger log = LoggerFactory.getLogger(SolverResultWriter.class);

    private final SolverJobRepository solverJobRepository;
    private final LessonRepository lessonRepository;
    private final WebSocketEventPublisher eventPublisher;

    public SolverResultWriter(
            SolverJobRepository solverJobRepository,
            LessonRepository lessonRepository,
            WebSocketEventPublisher eventPublisher) {
        this.solverJobRepository = solverJobRepository;
        this.lessonRepository = lessonRepository;
        this.eventPublisher = eventPublisher;
    }

    /** A new best solution: update the score and tell subscribers, without rewriting lessons. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProgress(Long jobId, TimetableSolution solution) {
        HardSoftScore score = solution.getScore();
        solverJobRepository.findById(jobId).ifPresent(job -> {
            applyScore(job, score);
            solverJobRepository.save(job);
            eventPublisher.publishToTopic(
                    WebSocketDestinations.solverProgressTopic(jobId),
                    new SolverProgressEvent(
                            jobId,
                            percentComplete(job),
                            score == null ? null : score.hardScore(),
                            score == null ? null : score.softScore()));
        });
    }

    /**
     * Final best solution: write the placements back and close the job.
     *
     * <p>A job cancelled by SCHED-05 keeps its CANCELLED status — but its lessons are still written,
     * which is what "best solution found before cancellation is retained" requires.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompletion(
            Long jobId, Long tenantId, Long timetableId, TimetableSolution solution) {
        try {
            List<Long> changed = writeLessons(tenantId, timetableId, solution);
            HardSoftScore score = solution.getScore();

            solverJobRepository.findById(jobId).ifPresent(job -> {
                applyScore(job, score);
                if (!SolverJobStatus.valueOf(job.getStatus()).isTerminal()) {
                    job.setStatus(SolverJobStatus.COMPLETED.name());
                }
                job.setCompletedAt(OffsetDateTime.now());
                solverJobRepository.save(job);
            });

            eventPublisher.publishToTopic(
                    WebSocketDestinations.solverCompleteTopic(jobId),
                    new SolverCompleteEvent(
                            jobId,
                            score == null ? null : score.hardScore(),
                            score == null ? null : score.softScore(),
                            describe(score),
                            changed));
            broadcastLessonUpdates(tenantId, timetableId, changed);
        } catch (RuntimeException e) {
            log.error("Failed to persist solver result for job {}", jobId, e);
            recordFailure(jobId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long jobId, Throwable throwable) {
        solverJobRepository.findById(jobId).ifPresent(job -> {
            if (!SolverJobStatus.valueOf(job.getStatus()).isTerminal()) {
                job.setStatus(SolverJobStatus.FAILED.name());
            }
            job.setErrorMessage(truncate(String.valueOf(throwable.getMessage())));
            job.setCompletedAt(OffsetDateTime.now());
            solverJobRepository.save(job);
        });
    }

    /** @return the ids whose slot actually moved */
    private List<Long> writeLessons(Long tenantId, Long timetableId, TimetableSolution solution) {
        Map<Long, PeriodSlot> slotByLessonId = new HashMap<>();
        for (com.schediflow.solver.model.Lesson planned : solution.getLessons()) {
            if (planned.getId() != null && planned.getPeriodSlot() != null) {
                slotByLessonId.put(planned.getId(), planned.getPeriodSlot());
            }
        }
        if (slotByLessonId.isEmpty()) {
            return List.of();
        }

        List<Long> changed = new java.util.ArrayList<>();
        for (Lesson lesson : lessonRepository
                .findByTenantIdAndTimetableIdOrderByScheduledDateAscSchedulePeriodIdAsc(tenantId, timetableId)) {
            PeriodSlot slot = slotByLessonId.get(lesson.getId());
            if (slot == null || lesson.isPinned()) {
                continue;
            }
            boolean moved = !Objects.equals(lesson.getSchedulePeriodId(), slot.getSchedulePeriodId())
                    || !Objects.equals(lesson.getScheduledDate(), slot.getDate());
            if (moved) {
                lesson.setSchedulePeriodId(slot.getSchedulePeriodId());
                lesson.setScheduledDate(slot.getDate());
                lessonRepository.save(lesson);
                changed.add(lesson.getId());
            }
        }
        return changed;
    }

    private void broadcastLessonUpdates(Long tenantId, Long timetableId, List<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return;
        }
        for (Lesson lesson : lessonRepository.findByIdInAndTenantId(lessonIds, tenantId)) {
            eventPublisher.publishToTopic(
                    WebSocketDestinations.timetableTopic(timetableId),
                    new LessonUpdatedEvent(
                            lesson.getId(),
                            timetableId,
                            lesson.getSchedulePeriodId(),
                            lesson.getRoomId(),
                            null,
                            lesson.isPinned(),
                            false));
        }
    }

    private static void applyScore(SolverJob job, HardSoftScore score) {
        if (score == null) {
            return;
        }
        // Timefold reports violations as a negative hard score; surface them as a positive count.
        job.setHardViolations(Math.abs(score.hardScore()));
        job.setSoftScore(score.softScore());
        job.setScoreBreakdown(describe(score));
    }

    private static String describe(HardSoftScore score) {
        if (score == null) {
            return null;
        }
        return score.hardScore() == 0
                ? "Feasible; soft score " + score.softScore()
                : Math.abs(score.hardScore()) + " hard constraint violation(s); soft score "
                        + score.softScore();
    }

    /** Timefold does not report search progress, so this is a coarse started/finished signal. */
    private static int percentComplete(SolverJob job) {
        return SolverJobStatus.valueOf(job.getStatus()).isTerminal() ? 100 : 50;
    }

    private static String truncate(String message) {
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
