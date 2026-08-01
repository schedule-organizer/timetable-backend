package com.schediflow.dto;

/**
 * Effective availability of a teacher for one weekday × period cell.
 * {@code UNAVAILABLE} is hard (a forbidden slot); the two preferred values are advisory only.
 */
public enum AvailabilityStatus {
    UNAVAILABLE,
    PREFERRED_FREE,
    PREFERRED_TEACHING,
    AVAILABLE
}
