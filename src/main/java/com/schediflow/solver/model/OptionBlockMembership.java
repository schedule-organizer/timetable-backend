package com.schediflow.solver.model;

import java.util.Objects;

/**
 * Problem fact: the teaching group runs inside this option block. Lessons of two groups sharing a
 * block must be scheduled in the same {@link PeriodSlot}. Populated from persisted
 * {@code option_block_groups} when building a {@link TimetableSolution}.
 */
public final class OptionBlockMembership {

    private final Long optionBlockId;
    private final Long teachingGroupId;

    public OptionBlockMembership(Long optionBlockId, Long teachingGroupId) {
        this.optionBlockId = Objects.requireNonNull(optionBlockId);
        this.teachingGroupId = Objects.requireNonNull(teachingGroupId);
    }

    public Long getOptionBlockId() {
        return optionBlockId;
    }

    public Long getTeachingGroupId() {
        return teachingGroupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OptionBlockMembership that = (OptionBlockMembership) o;
        return optionBlockId.equals(that.optionBlockId) && teachingGroupId.equals(that.teachingGroupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(optionBlockId, teachingGroupId);
    }
}
