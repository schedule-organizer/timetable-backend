package com.schediflow.domain;

/**
 * Timetable lifecycle. A timetable only ever moves forward: {@code DRAFT → PUBLISHED → ARCHIVED}.
 */
public enum TimetableStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    /** True when {@code next} is reachable from this state. Staying put is not a transition. */
    public boolean canTransitionTo(TimetableStatus next) {
        return switch (this) {
            case DRAFT -> next == PUBLISHED || next == ARCHIVED;
            case PUBLISHED -> next == ARCHIVED;
            case ARCHIVED -> false;
        };
    }
}
