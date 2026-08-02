package com.schediflow.dto.event;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Published when a solve terminates (SCHED-06).
 *
 * @param lessons ids whose placement actually changed, so a client can refresh just those cards
 */
public record SolverCompleteEvent(
        String event,
        Long jobId,
        Integer hardViolations,
        Integer softScore,
        String scoreBreakdown,
        List<Long> lessons,
        OffsetDateTime timestamp
) {

    public static final String TYPE = "SOLVER_COMPLETE";

    public SolverCompleteEvent(
            Long jobId, Integer hardScore, Integer softScore, String scoreBreakdown, List<Long> lessons) {
        this(TYPE, jobId, hardScore == null ? null : Math.abs(hardScore), softScore, scoreBreakdown,
                lessons, OffsetDateTime.now());
    }
}
