package com.schediflow.dto.response;

import java.time.OffsetDateTime;

public record TimetableResponse(
        Long id,
        String name,
        Long termId,
        Long bellScheduleId,
        String status,
        OffsetDateTime createdAt
) {}
