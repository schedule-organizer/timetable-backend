package com.schediflow.solver.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A concrete date plus bell-schedule period, used as a planning value and as holiday-forbidden facts.
 */
public final class PeriodSlot {

    private final LocalDate date;
    private final Long schedulePeriodId;
    private final int ordinal;

    public PeriodSlot(LocalDate date, Long schedulePeriodId, int ordinal) {
        this.date = Objects.requireNonNull(date);
        this.schedulePeriodId = Objects.requireNonNull(schedulePeriodId);
        this.ordinal = ordinal;
    }

    public LocalDate getDate() {
        return date;
    }

    public Long getSchedulePeriodId() {
        return schedulePeriodId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PeriodSlot that = (PeriodSlot) o;
        return ordinal == that.ordinal
                && date.equals(that.date)
                && schedulePeriodId.equals(that.schedulePeriodId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, schedulePeriodId, ordinal);
    }

    @Override
    public String toString() {
        return "PeriodSlot{" + "date=" + date + ", schedulePeriodId=" + schedulePeriodId + ", ordinal=" + ordinal + '}';
    }
}
