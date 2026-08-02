package com.schediflow.dto.response;

import java.time.OffsetDateTime;

/**
 * @param qualityScore human-readable summary, e.g. {@code "0 hard / -12 soft"}
 */
public record SolverJobResponse(
        Long jobId,
        Long timetableId,
        String status,
        String mode,
        Integer timeoutSeconds,
        String qualityScore,
        Integer hardViolations,
        Integer softScore,
        String scoreBreakdown,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {}
