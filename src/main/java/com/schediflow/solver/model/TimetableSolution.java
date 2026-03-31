package com.schediflow.solver.model;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal planning solution for Timefold: lessons choose period slots; holidays are hard forbidden via facts.
 */
@PlanningSolution
public class TimetableSolution {

    private List<Lesson> lessons = new ArrayList<>();
    private List<PeriodSlot> timeslotRange = new ArrayList<>();
    private List<UnavailablePeriodPenalty> holidayPenalties = new ArrayList<>();
    private HardSoftScore score;

    @PlanningEntityCollectionProperty
    public List<Lesson> getLessons() {
        return lessons;
    }

    public void setLessons(List<Lesson> lessons) {
        this.lessons = lessons;
    }

    @ValueRangeProvider(id = "timeslotRange")
    public List<PeriodSlot> getTimeslotRange() {
        return timeslotRange;
    }

    public void setTimeslotRange(List<PeriodSlot> timeslotRange) {
        this.timeslotRange = timeslotRange;
    }

    @ProblemFactCollectionProperty
    public List<UnavailablePeriodPenalty> getHolidayPenalties() {
        return holidayPenalties;
    }

    public void setHolidayPenalties(List<UnavailablePeriodPenalty> holidayPenalties) {
        this.holidayPenalties = holidayPenalties;
    }

    @PlanningScore
    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }
}
