package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChangeRoleRequest(@NotBlank String role) {}
