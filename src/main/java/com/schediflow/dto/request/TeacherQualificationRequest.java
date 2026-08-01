package com.schediflow.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TeacherQualificationRequest(
        @NotNull Long subjectId,
        @Min(1) Integer periodsPerCycle
) {}
