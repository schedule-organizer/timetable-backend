package com.schediflow.dto.response;

import com.schediflow.domain.ConflictType;

/**
 * One reason a lesson cannot sit where it is proposed to sit.
 *
 * @param conflictingLessonId the lesson already occupying the slot, or {@code null} for conflicts
 *                            that are not caused by another lesson (capacity, forbidden slots)
 */
public record ConflictViolation(
        ConflictType type,
        String message,
        Long conflictingLessonId
) {

    public static ConflictViolation of(ConflictType type, String message) {
        return new ConflictViolation(type, message, null);
    }

    public static ConflictViolation of(ConflictType type, String message, Long conflictingLessonId) {
        return new ConflictViolation(type, message, conflictingLessonId);
    }
}
