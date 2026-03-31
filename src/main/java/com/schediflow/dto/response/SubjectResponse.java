package com.schediflow.dto.response;

import java.time.OffsetDateTime;

public record SubjectResponse(
        Long id,
        String name,
        String code,
        String color,
        Integer difficultyLevel,
        String requiredRoomType,
        Integer maxPerDay,
        String spreadPattern,
        boolean active,
        OffsetDateTime createdAt
) {}
