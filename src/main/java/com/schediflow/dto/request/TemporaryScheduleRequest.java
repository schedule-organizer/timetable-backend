package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TemporaryScheduleRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Long baseTimetableId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
