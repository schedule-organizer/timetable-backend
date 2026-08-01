package com.schediflow.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.schediflow.solver.model.Lesson;
import com.schediflow.solver.model.PeriodSlot;
import com.schediflow.solver.model.TeacherSubjectQualification;
import com.schediflow.solver.model.TimetableSolution;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures {@link SchediFlowConstraintProvider} hard-penalizes lessons with a fixed teacher and subject when no matching
 * {@link TeacherSubjectQualification} fact exists, and accepts feasible solutions when the fact is present.
 */
class TeacherQualificationConstraintSolverTest {

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
    void lessonWithTeacherAndSubject_withoutQualificationFact_isInfeasible() {
        PeriodSlot slot = new PeriodSlot(LocalDate.of(2026, 5, 1), 1L, 1);

        TimetableSolution problem = new TimetableSolution();
        problem.setTimeslotRange(List.of(slot));
        problem.setHolidayPenalties(List.of());
        problem.setTeacherSubjectQualifications(List.of());

        Lesson lesson = new Lesson();
        lesson.setId(1L);
        lesson.setTeacherUserId(100L);
        lesson.setSubjectId(200L);
        lesson.setPeriodSlot(slot);
        problem.setLessons(List.of(lesson));

        Solver<TimetableSolution> solver = solverFactory.buildSolver();
        TimetableSolution solved = solver.solve(problem);

        assertThat(solved.getScore()).isNotNull();
        assertThat(solved.getScore().isFeasible()).isFalse();
    }

    @Test
    void lessonWithTeacherAndSubject_withMatchingFact_isFeasible() {
        PeriodSlot slot = new PeriodSlot(LocalDate.of(2026, 5, 2), 1L, 1);

        TimetableSolution problem = new TimetableSolution();
        problem.setTimeslotRange(List.of(slot));
        problem.setHolidayPenalties(List.of());
        problem.setTeacherSubjectQualifications(List.of(new TeacherSubjectQualification(100L, 200L)));

        Lesson lesson = new Lesson();
        lesson.setId(1L);
        lesson.setTeacherUserId(100L);
        lesson.setSubjectId(200L);
        lesson.setPeriodSlot(slot);
        problem.setLessons(List.of(lesson));

        Solver<TimetableSolution> solver = solverFactory.buildSolver();
        TimetableSolution solved = solver.solve(problem);

        assertThat(solved.getScore()).isNotNull();
        assertThat(solved.getScore().isFeasible()).isTrue();
    }
}
