package com.schediflow.domain;

/**
 * Advisory weekly preference a teacher expresses for a period. Soft only — the solver may override
 * either value, unlike a {@link ForbiddenSlot}.
 */
public enum TeacherPreferenceType {
    /** The teacher would rather not be timetabled here. */
    PREFERRED_FREE,
    /** The teacher would rather be timetabled here. */
    PREFERRED_TEACHING
}
