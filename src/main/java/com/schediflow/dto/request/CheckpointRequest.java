package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckpointRequest(@NotBlank @Size(max = 200) String name) {}
