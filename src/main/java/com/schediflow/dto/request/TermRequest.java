package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TermRequest(
        @NotNull Long academicYearId,
        @NotBlank @Size(max = 200) String name,
        @NotNull Integer ordinal,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
