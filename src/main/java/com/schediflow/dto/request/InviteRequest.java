package com.schediflow.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/users/invite.
 * Role defaults to TEACHER on the service layer.
 */
public record InviteRequest(@Email @NotBlank String email) {}
