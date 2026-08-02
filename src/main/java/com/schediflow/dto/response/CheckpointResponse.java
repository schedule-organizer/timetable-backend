package com.schediflow.dto.response;

import java.time.OffsetDateTime;

public record CheckpointResponse(
        Long id,
        Long timetableId,
        String name,
        int lessonCount,
        Long createdByUserId,
        OffsetDateTime createdAt
) {}
