package com.schediflow.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * One card in the timetable grid (SCHED-02).
 *
 * @param dayOfWeek 1 = Monday … 7 = Sunday, derived from {@code scheduledDate}
 */
public record TimetableLessonResponse(
        Long lessonId,
        String subjectName,
        String teacherName,
        String roomName,
        Long periodId,
        LocalDate scheduledDate,
        int dayOfWeek,
        @JsonProperty("isPinned") boolean isPinned,
        boolean hasConflict
) {}
