package com.schediflow.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BellScheduleRequest(
        @NotBlank @Size(max = 100) String name,
        @JsonProperty("isDefault") boolean isDefault,
        @NotNull @Valid List<PeriodRequest> periods
) {}
