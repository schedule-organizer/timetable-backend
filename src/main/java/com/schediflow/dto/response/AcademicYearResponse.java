package com.schediflow.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AcademicYearResponse(
        Long id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        @JsonProperty("isActive") boolean isActive,
        OffsetDateTime createdAt
) {}
