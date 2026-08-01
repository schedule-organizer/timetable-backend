package com.schediflow.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record TeachingGroupResponse(
        Long id,
        String name,
        String type,
        Long teacherId,
        Long subjectId,
        List<Long> classIds,
        boolean active,
        OffsetDateTime createdAt
) {}
