package com.schediflow.solver.model;

import java.util.Objects;

/**
 * Problem fact: scheduling a {@link Lesson} on this period slot is a hard constraint violation (e.g. holiday).
 */
public final class UnavailablePeriodPenalty {

    private final PeriodSlot periodSlot;

    public UnavailablePeriodPenalty(PeriodSlot periodSlot) {
        this.periodSlot = Objects.requireNonNull(periodSlot);
    }

    public PeriodSlot getPeriodSlot() {
        return periodSlot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UnavailablePeriodPenalty that = (UnavailablePeriodPenalty) o;
        return periodSlot.equals(that.periodSlot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(periodSlot);
    }
}
