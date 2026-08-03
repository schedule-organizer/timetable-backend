package com.schediflow.dto.response;

import java.time.OffsetDateTime;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String action,
        String entityType,
        Long entityId,
        String details,
        OffsetDateTime occurredAt
) {}
