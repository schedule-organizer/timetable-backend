package com.schediflow.dto.event;

import java.time.OffsetDateTime;

/**
 * Broadcast on the tenant topic when a cover teacher is assigned to a lesson (COVER-01).
 *
 * @param event discriminator so a single subscription can carry several event kinds
 */
public record CoverAssignedEvent(
        String event,
        Long lessonId,
        Long coverTeacherId,
        Long originalTeacherId,
        OffsetDateTime assignedAt
) {

    public static final String TYPE = "COVER_ASSIGNED";

    public CoverAssignedEvent(
            Long lessonId, Long coverTeacherId, Long originalTeacherId, OffsetDateTime assignedAt) {
        this(TYPE, lessonId, coverTeacherId, originalTeacherId, assignedAt);
    }
}
