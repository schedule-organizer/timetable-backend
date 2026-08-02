package com.schediflow.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Broadcast on every lesson mutation — move (SCHED-08), pin (SCHED-09), swap (SCHED-10) and solver
 * results (SCHED-06) — so open grids stay in step without polling.
 */
public record LessonUpdatedEvent(
        String event,
        Long lessonId,
        Long timetableId,
        Long periodId,
        Long roomId,
        Long teacherId,
        @JsonProperty("isPinned") boolean isPinned,
        boolean hasConflict,
        OffsetDateTime timestamp
) {

    public static final String TYPE = "LESSON_UPDATED";

    public LessonUpdatedEvent(
            Long lessonId,
            Long timetableId,
            Long periodId,
            Long roomId,
            Long teacherId,
            boolean isPinned,
            boolean hasConflict) {
        this(TYPE, lessonId, timetableId, periodId, roomId, teacherId, isPinned, hasConflict,
                OffsetDateTime.now());
    }
}
