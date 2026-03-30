package com.schediflow.dto.request;

import com.schediflow.domain.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HolidayDateRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 100) String name,
        @NotNull HolidayType type
) {}
