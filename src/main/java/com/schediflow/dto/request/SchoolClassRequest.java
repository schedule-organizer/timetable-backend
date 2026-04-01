package com.schediflow.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchoolClassRequest(
        @NotBlank @Size(max = 200) String name,
        Integer yearLevel,
        Long homeroomId,
        @Min(1) Integer capacity
) {}
