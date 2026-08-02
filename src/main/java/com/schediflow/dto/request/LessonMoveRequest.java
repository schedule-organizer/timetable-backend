package com.schediflow.dto.request;

/**
 * Both fields are optional, but at least one must be present — a move with neither is a no-op.
 * A null means "leave this as it is".
 */
public record LessonMoveRequest(Long periodId, Long roomId) {}
