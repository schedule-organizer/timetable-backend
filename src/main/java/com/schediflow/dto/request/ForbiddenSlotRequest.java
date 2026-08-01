package com.schediflow.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Recurring slots supply {@code dayOfWeek} (1 = Monday … 7 = Sunday); one-off slots supply
 * {@code specificDate}. Exactly one of the two must be present, matching {@code isRecurring}.
 */
public record ForbiddenSlotRequest(
        @NotBlank String entityType,
        @NotNull Long entityId,
        @Min(1) @Max(7) Integer dayOfWeek,
        LocalDate specificDate,
        @NotNull Long periodId,
        @JsonProperty("isRecurring") @NotNull Boolean isRecurring
) {}
