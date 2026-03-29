package com.schediflow.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;

public record PeriodResponse(
        Long id,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        @JsonProperty("isBreak") boolean isBreak,
        @JsonProperty("isLunch") boolean isLunch,
        Integer ordinal
) {}
