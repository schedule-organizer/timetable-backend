package com.schediflow.api.v1;

import com.schediflow.dto.request.SolverRunRequest;
import com.schediflow.dto.response.PagedResponse;
import com.schediflow.dto.response.SolverJobResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.SolverService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The scheduling engine: start a solve, watch it, stop it. ADMIN and MOD only — generating a
 * timetable rewrites everyone's schedule.
 */
@RestController
@RequestMapping("/api/v1/engine")
public class EngineController {

    private final SolverService solverService;

    public EngineController(SolverService solverService) {
        this.solverService = solverService;
    }

    /**
     * Starts an asynchronous solve and returns immediately (SCHED-03).
     *
     * @return 202 with the QUEUED job; 400 for an unknown mode or an excessive timeout;
     *         404 if the timetable is not in the tenant;
     *         409 if a job is already running for that timetable
     */
    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<SolverJobResponse> run(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody SolverRunRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(solverService.run(principal, request));
    }

    /** @return 200 with the job; 404 if not in the tenant */
    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<SolverJobResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(solverService.getJob(id));
    }

    /** @return 200 with a page of jobs, newest first, optionally filtered by timetable */
    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<PagedResponse<SolverJobResponse>> listJobs(
            @RequestParam(required = false) Long timetableId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SolverJobResponse> jobs =
                solverService.listJobs(timetableId, PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(PagedResponse.from(jobs, java.util.function.Function.identity()));
    }

    /**
     * Stops a running solve (SCHED-05). Whatever the solver had found is already persisted.
     *
     * @return 200 with the cancelled job; 400 if already terminal; 404 if not in the tenant
     */
    @PostMapping("/jobs/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<SolverJobResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(solverService.cancel(id));
    }
}
