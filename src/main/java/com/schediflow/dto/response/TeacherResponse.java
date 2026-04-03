package com.schediflow.dto.response;

import java.time.OffsetDateTime;

public record TeacherResponse(
        Long id,
        Long userId,
        String displayName,
        Integer maxPeriodsPerDay,
        Integer maxConsecutivePeriods,
        Integer workloadCap,
        boolean active,
        OffsetDateTime createdAt
) {}
