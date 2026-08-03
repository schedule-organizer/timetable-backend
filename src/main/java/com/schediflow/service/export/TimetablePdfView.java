package com.schediflow.service.export;

/**
 * Which axis a printed timetable is organised by (EXPORT-01).
 *
 * <p>The grid is always periods × days; the view decides what each row is grouped under and what
 * detail each cell shows.</p>
 */
public enum TimetablePdfView {
    CLASS,
    TEACHER,
    ROOM
}
