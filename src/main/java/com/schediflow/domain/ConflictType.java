package com.schediflow.domain;

/**
 * Kinds of scheduling conflict {@code ConflictDetectionService} reports (SCHED-11).
 */
public enum ConflictType {
    TEACHER_DOUBLE_BOOKED,
    ROOM_DOUBLE_BOOKED,
    CLASS_DOUBLE_BOOKED,
    ROOM_CAPACITY_EXCEEDED,
    TEACHER_FORBIDDEN_SLOT,
    ROOM_FORBIDDEN_SLOT,
    CLASS_FORBIDDEN_SLOT
}
