package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TimetableStatusRequest(@NotBlank String status) {}
