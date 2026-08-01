package com.schediflow.dto.request;

import com.schediflow.dto.SpreadPattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ClassSubjectHourItemRequest(
        @NotNull Long subjectId,
        @Positive int periodsPerCycle,
        @NotNull SpreadPattern spreadPattern) {}
