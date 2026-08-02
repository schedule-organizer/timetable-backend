package com.schediflow.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param mode           FAST / BALANCED / THOROUGH; defaults to BALANCED when omitted
 * @param timeoutSeconds optional override of the mode's default search time
 * @param scope          optional (SCHED-14): when present, only lessons matching it may move
 */
public record SolverRunRequest(
        @NotNull Long timetableId,
        String mode,
        @Min(1) Integer timeoutSeconds,
        SolverScope scope
) {}
