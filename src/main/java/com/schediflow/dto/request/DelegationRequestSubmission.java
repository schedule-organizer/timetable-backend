package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DelegationRequestSubmission(
        @NotBlank String type,
        @NotEmpty List<Long> lessonIds,
        @NotNull Long targetTeacherId,
        @Size(max = 500) String reason
) {}
