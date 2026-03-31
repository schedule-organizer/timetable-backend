package com.schediflow.dto.response;

import java.time.OffsetDateTime;

public record HolidayCalendarResponse(
        Long id,
        Long academicYearId,
        String name,
        String country,
        String region,
        OffsetDateTime createdAt
) {}
