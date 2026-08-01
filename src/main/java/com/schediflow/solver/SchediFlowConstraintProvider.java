package com.schediflow.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.schediflow.solver.model.Lesson;
import com.schediflow.solver.model.OptionBlockMembership;
import com.schediflow.solver.model.TeacherSubjectQualification;
import com.schediflow.solver.model.UnavailablePeriodPenalty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SchediFlowConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            holidaySlotMustBeFree(constraintFactory),
            teacherMustBeQualifiedForSubject(constraintFactory),
            optionBlockGroupsMustShareAPeriod(constraintFactory)
        };
    }

    private Constraint holidaySlotMustBeFree(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(Lesson.class)
                .join(
                        UnavailablePeriodPenalty.class,
                        Joiners.filtering(
                                (lesson, penalty) ->
                                        lesson.getPeriodSlot() != null
                                                && lesson.getPeriodSlot().equals(penalty.getPeriodSlot())))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Holiday slot must be free");
    }

    private Constraint teacherMustBeQualifiedForSubject(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(Lesson.class)
                .filter(lesson -> lesson.getTeacherUserId() != null && lesson.getSubjectId() != null)
                .ifNotExists(
                        TeacherSubjectQualification.class,
                        Joiners.filtering(
                                (lesson, q) ->
                                        Objects.equals(lesson.getTeacherUserId(), q.getTeacherUserId())
                                                && Objects.equals(lesson.getSubjectId(), q.getSubjectId())))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher must be qualified for subject");
    }

    /**
     * Two lessons belonging to different teaching groups of the same option block must run in the same
     * period slot, so a student can pick exactly one of the options without a clash.
     */
    private Constraint optionBlockGroupsMustShareAPeriod(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(Lesson.class)
                .filter((left, right) ->
                        left.getTeachingGroupId() != null
                                && right.getTeachingGroupId() != null
                                && !Objects.equals(left.getTeachingGroupId(), right.getTeachingGroupId())
                                && left.getPeriodSlot() != null
                                && right.getPeriodSlot() != null
                                && !left.getPeriodSlot().equals(right.getPeriodSlot()))
                .join(
                        OptionBlockMembership.class,
                        Joiners.filtering((left, right, membership) ->
                                Objects.equals(membership.getTeachingGroupId(), left.getTeachingGroupId())))
                .join(
                        OptionBlockMembership.class,
                        Joiners.filtering((left, right, leftMembership, rightMembership) ->
                                Objects.equals(rightMembership.getTeachingGroupId(), right.getTeachingGroupId())
                                        && Objects.equals(
                                                leftMembership.getOptionBlockId(),
                                                rightMembership.getOptionBlockId())))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Option block groups must share a period");
    }
}
