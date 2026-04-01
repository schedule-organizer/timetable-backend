package com.schediflow.dto.response;

import java.time.LocalDate;

/**
 * Warning item when a published lesson is scheduled on a date that was just marked as a holiday.
 */
public record HolidayLessonConflictResponse(
        Long lessonId,
        String subjectName,
        String teacherName,
        String className,
        LocalDate conflictingDate
) {}
