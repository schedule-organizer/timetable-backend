package com.schediflow.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.schediflow.solver.model.Lesson;
import com.schediflow.solver.model.OptionBlockMembership;
import com.schediflow.solver.model.PeriodSlot;
import com.schediflow.solver.model.TimetableSolution;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures {@link SchediFlowConstraintProvider} hard-penalizes lessons of two teaching groups in the same
 * option block when they are scheduled on different period slots (RES-08).
 */
class OptionBlockConstraintSolverTest {

    private static SolverFactory<TimetableSolution> solverFactory;

    @BeforeAll
    static void createSolverFactory() {
        SolverConfig config = new SolverConfig();
        config.setSolutionClass(TimetableSolution.class);
        config.setEntityClassList(List.of(Lesson.class));
        ScoreDirectorFactoryConfig scoreDirector = new ScoreDirectorFactoryConfig();
        scoreDirector.setConstraintProviderClass(SchediFlowConstraintProvider.class);
        config.setScoreDirectorFactoryConfig(scoreDirector);
        TerminationConfig termination = new TerminationConfig();
        termination.setSecondsSpentLimit(5L);
        config.setTerminationConfig(termination);
        solverFactory = SolverFactory.create(config);
    }

    @Test
    void memberGroupsOnDifferentSlots_areResolvedToTheSameSlot() {
        PeriodSlot first = new PeriodSlot(LocalDate.of(2026, 5, 4), 1L, 1);
        PeriodSlot second = new PeriodSlot(LocalDate.of(2026, 5, 4), 2L, 2);

        TimetableSolution problem = problem(first, second);
        problem.setOptionBlockMemberships(
                List.of(new OptionBlockMembership(7L, 70L), new OptionBlockMembership(7L, 71L)));
        problem.setLessons(List.of(lesson(1L, 70L, first), lesson(2L, 71L, second)));

        TimetableSolution solved = solverFactory.buildSolver().solve(problem);

        assertThat(solved.getScore().isFeasible()).isTrue();
        assertThat(solved.getLessons().get(0).getPeriodSlot())
                .isEqualTo(solved.getLessons().get(1).getPeriodSlot());
    }

    @Test
    void memberGroupsOnTheSameSlot_areFeasible() {
        PeriodSlot slot = new PeriodSlot(LocalDate.of(2026, 5, 5), 1L, 1);

        TimetableSolution problem = problem(slot);
        problem.setOptionBlockMemberships(
                List.of(new OptionBlockMembership(7L, 70L), new OptionBlockMembership(7L, 71L)));
        problem.setLessons(List.of(lesson(1L, 70L, slot), lesson(2L, 71L, slot)));

        TimetableSolution solved = solverFactory.buildSolver().solve(problem);

        assertThat(solved.getScore().isFeasible()).isTrue();
        assertThat(solved.getScore().hardScore()).isZero();
    }

    @Test
    void groupsInDifferentBlocks_mayUseDifferentSlots() {
        PeriodSlot first = new PeriodSlot(LocalDate.of(2026, 5, 6), 1L, 1);
        PeriodSlot second = new PeriodSlot(LocalDate.of(2026, 5, 6), 2L, 2);

        TimetableSolution problem = problem(first, second);
        problem.setOptionBlockMemberships(
                List.of(new OptionBlockMembership(7L, 70L), new OptionBlockMembership(8L, 71L)));
        problem.setLessons(List.of(lesson(1L, 70L, first), lesson(2L, 71L, second)));

        TimetableSolution solved = solverFactory.buildSolver().solve(problem);

        assertThat(solved.getScore().isFeasible()).isTrue();
        assertThat(solved.getScore().hardScore()).isZero();
    }

    private static TimetableSolution problem(PeriodSlot... slots) {
        TimetableSolution problem = new TimetableSolution();
        problem.setTimeslotRange(List.of(slots));
        problem.setHolidayPenalties(List.of());
        problem.setTeacherSubjectQualifications(List.of());
        return problem;
    }

    private static Lesson lesson(Long id, Long teachingGroupId, PeriodSlot slot) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setTeachingGroupId(teachingGroupId);
        lesson.setPeriodSlot(slot);
        return lesson;
    }
}
