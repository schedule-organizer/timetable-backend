package com.schediflow.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for POST /api/v1/auth/register.
 */
public record RegisterRequest(

        @NotBlank(message = "Institution name is required")
        String institutionName,

        @Email(message = "Email must be a valid email address")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(
            regexp = "^(?=.*[0-9]).{8,}$",
            message = "Password must be at least 8 characters and contain at least one number"
        )
        String password
) {}
