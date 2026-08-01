package com.schediflow.dto.response;

import java.time.OffsetDateTime;

public record CoverAssignmentResponse(
        Long id,
        Long lessonId,
        Long coverTeacherId,
        Long originalTeacherUserId,
        String reason,
        Long assignedBy,
        OffsetDateTime assignedAt
) {}
