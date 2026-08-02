package com.schediflow.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CoverAssignmentRequest(
        @NotNull Long lessonId,
        @NotNull Long coverTeacherId,
        @Size(max = 500) String reason
) {}
