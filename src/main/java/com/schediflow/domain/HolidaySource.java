package com.schediflow.domain;

/**
 * Indicates how a {@link HolidayDate} was created.
 */
public enum HolidaySource {
    /** Imported from a public holiday feed (e.g. Calendarific). */
    IMPORTED,
    /** Created manually via the API. */
    MANUAL
}
