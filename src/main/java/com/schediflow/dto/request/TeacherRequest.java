package com.schediflow.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeacherRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 200) String displayName,
        @Min(0) Integer maxPeriodsPerDay,
        @Min(0) Integer maxConsecutivePeriods,
        @Min(0) Integer workloadCap
) {}
