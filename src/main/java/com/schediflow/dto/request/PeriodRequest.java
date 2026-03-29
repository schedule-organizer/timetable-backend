package com.schediflow.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record PeriodRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @JsonProperty("isBreak") boolean isBreak,
        @JsonProperty("isLunch") boolean isLunch,
        @NotNull Integer ordinal
) {}
