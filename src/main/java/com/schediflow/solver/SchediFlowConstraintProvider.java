package com.schediflow.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.schediflow.solver.model.Lesson;
import com.schediflow.solver.model.UnavailablePeriodPenalty;
import org.springframework.stereotype.Component;

@Component
public class SchediFlowConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] { holidaySlotMustBeFree(constraintFactory) };
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
}
