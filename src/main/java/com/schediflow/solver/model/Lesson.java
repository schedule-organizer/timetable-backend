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
}
