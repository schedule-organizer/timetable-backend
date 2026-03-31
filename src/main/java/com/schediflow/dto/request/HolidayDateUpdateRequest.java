package com.schediflow.dto.request;

import com.schediflow.domain.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HolidayDateUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull HolidayType type
) {}
