package com.schediflow.dto.request;

import jakarta.validation.constraints.NotNull;

public record LessonSwapRequest(@NotNull Long targetLessonId) {}
