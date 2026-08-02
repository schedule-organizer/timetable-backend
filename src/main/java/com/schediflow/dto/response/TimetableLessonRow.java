package com.schediflow.dto.response;

import java.time.LocalDate;

/**
 * Flat projection of a grid row, populated by a single joined query so rendering the grid never
 * triggers per-lesson lookups. Turned into a {@link TimetableLessonResponse} once the conflict
 * flags are known.
 */
public record TimetableLessonRow(
        Long lessonId,
        String subjectName,
        String teacherName,
        String roomName,
        Long periodId,
        LocalDate scheduledDate,
        boolean isPinned,
        Long teacherUserId,
        Long classId,
        Long roomId
) {}
