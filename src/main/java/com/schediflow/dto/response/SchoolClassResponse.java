package com.schediflow.dto.response;

import java.time.OffsetDateTime;

public record SchoolClassResponse(
        Long id,
        String name,
        Integer yearLevel,
        Long homeroomId,
        Integer capacity,
        boolean active,
        OffsetDateTime createdAt
) {}
