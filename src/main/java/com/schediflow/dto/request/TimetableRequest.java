package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param bellScheduleId optional; defaults to the tenant's default bell schedule
 */
public record TimetableRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Long termId,
        Long bellScheduleId
) {}
