package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveTemplateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @Size(max = 64) String institutionType
) {}
