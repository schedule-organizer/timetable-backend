package com.schediflow.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AcademicYearRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @JsonProperty("isActive") boolean isActive
) {}
