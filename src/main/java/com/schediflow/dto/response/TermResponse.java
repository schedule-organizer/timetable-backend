package com.schediflow.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TermResponse(
        Long id,
        Long academicYearId,
        String name,
        Integer ordinal,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime createdAt
) {}
