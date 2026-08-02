package com.schediflow.service.delegation;

import com.schediflow.domain.DelegationType;
import com.schediflow.domain.Lesson;
import com.schediflow.exception.ConflictException;
import com.schediflow.service.delegation.DelegationReassignmentPlanner.Reassignment;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelegationReassignmentPlannerTest {

    private static final Long REQUESTER = 100L;
    private static final Long TARGET = 200L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);
    private static final Long PERIOD_1 = 1L;
    private static final Long PERIOD_2 = 2L;

    @Test
    void handover_movesEveryRequestedLessonToTheTarget() {
        Lesson a = lesson(1L, REQUESTER, PERIOD_1);
        Lesson b = lesson(2L, REQUESTER, PERIOD_2);

        List<Reassignment> plan = DelegationReassignmentPlanner.plan(
                DelegationType.HANDOVER, List.of(a, b), List.of(), REQUESTER, TARGET);

        assertThat(plan).hasSize(2);
        assertThat(plan).allMatch(r -> r.newTeacherUserId().equals(TARGET));
    }

    @Test
    void handover_ignoresTheTargetsOwnLessons() {
        Lesson mine = lesson(1L, REQUESTER, PERIOD_1);
        Lesson theirs = lesson(9L, TARGET, PERIOD_1);

        List<Reassignment> plan = DelegationReassignmentPlanner.plan(
                DelegationType.HANDOVER, List.of(mine), List.of(theirs), REQUESTER, TARGET);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).lesson().getId()).isEqualTo(1L);
    }

    @Test
    void swap_exchangesLessonsSharingASlot() {
        Lesson mine = lesson(1L, REQUESTER, PERIOD_1);
        Lesson theirs = lesson(9L, TARGET, PERIOD_1);

        List<Reassignment> plan = DelegationReassignmentPlanner.plan(
                DelegationType.SWAP, List.of(mine), List.of(theirs), REQUESTER, TARGET);

        assertThat(plan).hasSize(2);
        assertThat(plan).anySatisfy(r -> {
            assertThat(r.lesson().getId()).isEqualTo(1L);
            assertThat(r.newTeacherUserId()).isEqualTo(TARGET);
        });
        assertThat(plan).anySatisfy(r -> {
            assertThat(r.lesson().getId()).isEqualTo(9L);
            assertThat(r.newTeacherUserId()).isEqualTo(REQUESTER);
        });
    }

    @Test
    void swap_withNothingInTheSlot_degradesToAMove() {
        Lesson mine = lesson(1L, REQUESTER, PERIOD_1);
        Lesson theirsElsewhere = lesson(9L, TARGET, PERIOD_2);

        List<Reassignment> plan = DelegationReassignmentPlanner.plan(
                DelegationType.SWAP, List.of(mine), List.of(theirsElsewhere), REQUESTER, TARGET);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).newTeacherUserId()).isEqualTo(TARGET);
    }

    @Test
    void swap_neverPlansTheSameCounterpartTwice() {
        Lesson mineOne = lesson(1L, REQUESTER, PERIOD_1);
        Lesson mineTwo = lesson(2L, REQUESTER, PERIOD_1);
        Lesson theirs = lesson(9L, TARGET, PERIOD_1);

        List<Reassignment> plan = DelegationReassignmentPlanner.plan(
                DelegationType.SWAP, List.of(mineOne, mineTwo), List.of(theirs), REQUESTER, TARGET);

        assertThat(plan.stream().filter(r -> r.lesson().getId().equals(9L))).hasSize(1);
    }

    @Test
    void conflictCheck_passesForACleanSwap() {
        Lesson mine = lesson(1L, REQUESTER, PERIOD_1);
        Lesson theirs = lesson(9L, TARGET, PERIOD_1);
        List<Reassignment> plan = DelegationReassignmentPlanner.plan(
                DelegationType.SWAP, List.of(mine), List.of(theirs), REQUESTER, TARGET);

        assertThatCode(() -> DelegationReassignmentPlanner.assertNoConflicts(
                        plan, Map.of(REQUESTER, List.of(mine), TARGET, List.of(theirs))))
                .doesNotThrowAnyException();
    }

    @Test
    void conflictCheck_rejectsAHandoverIntoAnOccupiedSlot() {
        Lesson mine = lesson(1L, REQUESTER, PERIOD_1);
        Lesson theirs = lesson(9L, TARGET, PERIOD_1);
        List<Reassignment> plan = DelegationReassignmentPlanner.plan(
                DelegationType.HANDOVER, List.of(mine), List.of(theirs), REQUESTER, TARGET);

        assertThatThrownBy(() -> DelegationReassignmentPlanner.assertNoConflicts(
                        plan, Map.of(REQUESTER, List.of(mine), TARGET, List.of(theirs))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("double-book");
    }

    @Test
    void conflictCheck_ignoresSlotsFreedByTheSamePlan() {
        // The requester hands over period 1 and the target has nothing there: no clash either side.
        Lesson mine = lesson(1L, REQUESTER, PERIOD_1);
        Lesson alsoMine = lesson(2L, REQUESTER, PERIOD_2);
        List<Reassignment> plan = DelegationReassignmentPlanner.plan(
                DelegationType.HANDOVER, List.of(mine), List.of(), REQUESTER, TARGET);

        assertThatCode(() -> DelegationReassignmentPlanner.assertNoConflicts(
                        plan, Map.of(REQUESTER, List.of(mine, alsoMine), TARGET, List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void conflictCheck_rejectsTwoRequestedLessonsLandingOnOneSlot() {
        Lesson one = lesson(1L, REQUESTER, PERIOD_1);
        Lesson two = lesson(2L, REQUESTER, PERIOD_1);
        List<Reassignment> plan = DelegationReassignmentPlanner.plan(
                DelegationType.HANDOVER, List.of(one, two), List.of(), REQUESTER, TARGET);

        assertThatThrownBy(() -> DelegationReassignmentPlanner.assertNoConflicts(
                        plan, Map.of(REQUESTER, List.of(one, two), TARGET, List.of())))
                .isInstanceOf(ConflictException.class);
    }

    private static Lesson lesson(Long id, Long teacherUserId, Long periodId) {
        Lesson lesson = new Lesson();
        ReflectionTestUtils.setField(lesson, "id", id);
        lesson.setTeacherUserId(teacherUserId);
        lesson.setSchedulePeriodId(periodId);
        lesson.setScheduledDate(DAY);
        return lesson;
    }
}
