package com.schediflow.dto.event;

import java.time.OffsetDateTime;

/** Published on each new best solution (SCHED-06). */
public record SolverProgressEvent(
        String event,
        Long jobId,
        int percentComplete,
        Integer hardViolations,
        Integer softScore,
        OffsetDateTime timestamp
) {

    public static final String TYPE = "SOLVER_PROGRESS";

    public SolverProgressEvent(Long jobId, int percentComplete, Integer hardScore, Integer softScore) {
        this(TYPE, jobId, percentComplete,
                hardScore == null ? null : Math.abs(hardScore), softScore, OffsetDateTime.now());
    }
}
