package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/auth/complete-registration.
 * The raw invitation token is sent as-is; the service hashes it for lookup.
 */
public record CompleteRegistrationRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 128) String password,
        String displayName) {}
