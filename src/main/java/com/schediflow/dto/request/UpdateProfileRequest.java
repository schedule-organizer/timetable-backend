package com.schediflow.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/v1/users/me.
 * All fields are optional; currentPassword is required when newPassword is provided.
 */
public record UpdateProfileRequest(
        String displayName,
        String currentPassword,
        @Size(min = 8, max = 128) String newPassword) {}
