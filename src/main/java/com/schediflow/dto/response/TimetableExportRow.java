package com.schediflow.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A lesson flattened for export, with every referenced name already resolved by one joined query
 * so exporting never triggers per-lesson lookups (EXPORT-01/02/03).
 */
public record TimetableExportRow(
        Long lessonId,
        String subjectName,
        String teacherName,
        String roomName,
        String className,
        LocalDate scheduledDate,
        String periodName,
        Integer periodOrdinal,
        LocalTime startTime,
        LocalTime endTime,
        Long teacherUserId,
        Long classId,
        Long roomId
) {

    /** 1 = Monday … 7 = Sunday. */
    public int dayOfWeek() {
        return scheduledDate.getDayOfWeek().getValue();
    }
}
