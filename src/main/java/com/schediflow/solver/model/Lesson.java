package com.schediflow.solver.model;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

/**
 * Minimal planning entity for holiday constraint verification; expanded in SCHED-03.
 */
@PlanningEntity
public class Lesson {

    @PlanningId
    private Long id;

    /** When set with {@link #subjectId}, the solver requires a matching {@link TeacherSubjectQualification} fact. */
    private Long teacherUserId;

    private Long subjectId;

    /** When set, lessons of groups sharing an {@link OptionBlockMembership} must land on the same slot. */
    private Long teachingGroupId;

    @PlanningVariable(valueRangeProviderRefs = "timeslotRange")
    private PeriodSlot periodSlot;

    public Lesson() {
    }

    public Lesson(Long id, PeriodSlot periodSlot) {
        this.id = id;
        this.periodSlot = periodSlot;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PeriodSlot getPeriodSlot() {
        return periodSlot;
    }

    public void setPeriodSlot(PeriodSlot periodSlot) {
        this.periodSlot = periodSlot;
    }

    public Long getTeacherUserId() {
        return teacherUserId;
    }

    public void setTeacherUserId(Long teacherUserId) {
        this.teacherUserId = teacherUserId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getTeachingGroupId() {
        return teachingGroupId;
    }

    public void setTeachingGroupId(Long teachingGroupId) {
        this.teachingGroupId = teachingGroupId;
    }
}
