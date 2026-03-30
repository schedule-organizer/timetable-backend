package com.schediflow.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

public record BellScheduleResponse(
        Long id,
        String name,
        @JsonProperty("isDefault") boolean isDefault,
        List<PeriodResponse> periods,
        OffsetDateTime createdAt
) {}
