package com.schediflow.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ForbiddenSlotResponse(
        Long id,
        String entityType,
        Long entityId,
        Integer dayOfWeek,
        LocalDate specificDate,
        Long periodId,
        @JsonProperty("isRecurring") boolean isRecurring,
        OffsetDateTime createdAt
) {}
