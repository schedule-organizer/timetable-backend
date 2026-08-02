package com.schediflow.domain;

/**
 * Lifecycle of a temporary schedule. COVER-06 moves ACTIVE overlays to EXPIRED once their end date
 * has passed.
 */
public enum TemporaryScheduleStatus {
    ACTIVE,
    EXPIRED
}
