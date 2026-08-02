package com.schediflow.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record DelegationRequestResponse(
        Long id,
        String type,
        String status,
        Long requestedByUserId,
        Long targetTeacherId,
        List<Long> lessonIds,
        String reason,
        String rejectionReason,
        Long decidedBy,
        OffsetDateTime decidedAt,
        OffsetDateTime createdAt
) {}
