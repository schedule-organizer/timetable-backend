package com.schediflow.dto.event;

import java.time.OffsetDateTime;

/** Broadcast on the tenant topic when a timetable goes live (SCHED-07). */
public record TimetablePublishedEvent(
        String event,
        Long timetableId,
        Long termId,
        OffsetDateTime publishedAt
) {

    public static final String TYPE = "TIMETABLE_PUBLISHED";

    public TimetablePublishedEvent(Long timetableId, Long termId, OffsetDateTime publishedAt) {
        this(TYPE, timetableId, termId, publishedAt);
    }
}
