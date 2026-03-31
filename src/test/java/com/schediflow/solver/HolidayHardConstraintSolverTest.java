package com.schediflow.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.schediflow.solver.model.Lesson;
import com.schediflow.solver.model.PeriodSlot;
import com.schediflow.solver.model.TimetableSolution;
import com.schediflow.solver.model.UnavailablePeriodPenalty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies holiday {@link UnavailablePeriodPenalty} facts produce a hard score when a lesson uses a forbidden slot,
 * and the solver can move the lesson to a feasible slot.
 */
class HolidayHardConstraintSolverTest {

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
    void solver_movesLessonOffHolidaySlot() {
        LocalDate dHoliday = LocalDate.of(2026, 3, 15);
        LocalDate dFree = LocalDate.of(2026, 3, 16);

        PeriodSlot holidaySlot = new PeriodSlot(dHoliday, 1L, 1);
        PeriodSlot freeSlot = new PeriodSlot(dFree, 1L, 1);

        TimetableSolution problem = new TimetableSolution();
        problem.setTimeslotRange(List.of(holidaySlot, freeSlot));
        problem.setHolidayPenalties(List.of(new UnavailablePeriodPenalty(holidaySlot)));

        Lesson lesson = new Lesson();
        lesson.setId(1L);
        lesson.setPeriodSlot(holidaySlot);
        problem.setLessons(List.of(lesson));

        Solver<TimetableSolution> solver = solverFactory.buildSolver();
        TimetableSolution solved = solver.solve(problem);

        assertThat(solved.getScore()).isNotNull();
        assertThat(solved.getScore().isFeasible()).isTrue();
        assertThat(solved.getLessons().get(0).getPeriodSlot()).isEqualTo(freeSlot);
    }

    @Test
    void lessonOnHolidaySlot_scoresHardPenalty() {
        PeriodSlot holidaySlot = new PeriodSlot(LocalDate.of(2026, 4, 1), 9L, 2);

        TimetableSolution problem = new TimetableSolution();
        problem.setTimeslotRange(List.of(holidaySlot));
        problem.setHolidayPenalties(List.of(new UnavailablePeriodPenalty(holidaySlot)));

        Lesson lesson = new Lesson();
        lesson.setId(1L);
        lesson.setPeriodSlot(holidaySlot);
        problem.setLessons(List.of(lesson));

        Solver<TimetableSolution> solver = solverFactory.buildSolver();
        TimetableSolution solved = solver.solve(problem);

        assertThat(solved.getScore()).isNotNull();
        assertThat(solved.getScore().isFeasible()).isFalse();
    }
}
