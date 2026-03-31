package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HolidayCalendarRequest(
        @NotNull Long academicYearId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String country,
        @Size(max = 100) String region
) {}
