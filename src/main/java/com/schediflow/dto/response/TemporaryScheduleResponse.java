package com.schediflow.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TemporaryScheduleResponse(
        Long id,
        String name,
        Long baseTimetableId,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        long overrideCount,
        OffsetDateTime createdAt
) {}
