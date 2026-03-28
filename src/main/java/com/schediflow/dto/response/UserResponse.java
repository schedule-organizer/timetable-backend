package com.schediflow.dto.response;

import java.time.OffsetDateTime;

/**
 * Public representation of a user — password hash is never included.
 */
public record UserResponse(
        Long id,
        String email,
        String displayName,
        String role,
        String status,
        OffsetDateTime createdAt) {}
