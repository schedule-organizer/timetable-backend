package com.schediflow.service;

import com.schediflow.config.DemoDataProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the dataset really is driven by {@code app.demo}, and that a bad edit fails loudly.
 *
 * <p>Configuration you cannot mis-set safely is worse than a constant: the failure mode of a
 * curriculum that no longer fills the cycle is not an error, it is a timetable that cannot be solved
 * for reasons nobody can see. Each test here overrides one property and asserts the consequence.</p>
 */
class DemoDataSeederConfigTest {

    /** A different school shape entirely: a 12-class primary on a 5-day, 5-period week. */
    @SpringBootTest
    @ActiveProfiles("demo")
    @TestPropertySource(properties = {
        "app.demo.auto-seed=false",
        "app.demo.cycle.days=5",
        "app.demo.cycle.periods-per-day=5",
        "app.demo.cycle.lunch-after-period=2",
        "app.demo.classes.grades[0]=1",
        "app.demo.classes.grades[1]=2",
        "app.demo.classes.grades[2]=3",
        "app.demo.classes.grades[3]=4",
        "app.demo.classes.grades[4]=5",
        "app.demo.classes.grades[5]=6",
        "app.demo.classes.sections[0]=A",
        "app.demo.classes.sections[1]=B",
        // A 25-period week: 10 + 8 + 4 + 3.
        "app.demo.subjects[0].code=MATH",
        "app.demo.subjects[0].name=Mathematics",
        "app.demo.subjects[0].periods-per-cycle=10",
        "app.demo.subjects[0].teachers=4",
        "app.demo.subjects[0].color=#3B82F6",
        "app.demo.subjects[0].difficulty=3",
        "app.demo.subjects[0].spread-pattern=SPREAD",
        "app.demo.subjects[0].max-per-day=2",
        "app.demo.subjects[1].code=LANG",
        "app.demo.subjects[1].name=Language",
        "app.demo.subjects[1].periods-per-cycle=8",
        "app.demo.subjects[1].teachers=3",
        "app.demo.subjects[1].color=#8B5CF6",
        "app.demo.subjects[1].difficulty=3",
        "app.demo.subjects[1].spread-pattern=SPREAD",
        "app.demo.subjects[1].max-per-day=2",
        "app.demo.subjects[2].code=PE",
        "app.demo.subjects[2].name=Physical Education",
        "app.demo.subjects[2].periods-per-cycle=4",
        "app.demo.subjects[2].teachers=2",
        "app.demo.subjects[2].color=#EC4899",
        "app.demo.subjects[2].difficulty=1",
        "app.demo.subjects[2].spread-pattern=SPREAD",
        "app.demo.subjects[2].required-room-type=GYM",
        "app.demo.subjects[2].max-per-day=1",
        "app.demo.subjects[3].code=ART",
        "app.demo.subjects[3].name=Art",
        "app.demo.subjects[3].periods-per-cycle=3",
        "app.demo.subjects[3].teachers=2",
        "app.demo.subjects[3].color=#F97316",
        "app.demo.subjects[3].difficulty=1",
        "app.demo.subjects[3].spread-pattern=ANY",
        "app.demo.subjects[3].required-room-type=OTHER",
        "app.demo.subjects[3].max-per-day=1",
    })
    static class PrimarySchoolShape {

        @Autowired DemoDataSeeder seeder;
        @Autowired DemoDataProperties properties;
        @Autowired JdbcTemplate jdbcTemplate;

        @Test
        void seedsTheReconfiguredSchool() {
            assertThat(properties.classCount()).isEqualTo(12);   // 6 grades x 2 sections
            assertThat(properties.slotsPerClass()).isEqualTo(25); // 5 days x 5 periods
            assertThat(properties.teacherCount()).isEqualTo(11);
            assertThat(properties.expectedLessonsPerCycle()).isEqualTo(300);

            DemoDataSeeder.DemoDataset dataset = seeder.seed("primary-" + UUID.randomUUID());

            assertThat(dataset.classIds()).hasSize(12);
            assertThat(dataset.teacherUserIds()).hasSize(11);
            assertThat(dataset.teachingPeriodIds()).hasSize(5);
            assertThat(dataset.expectedLessonsPerCycle()).isEqualTo(300);

            Integer demanded = jdbcTemplate.queryForObject(
                    "SELECT SUM(periods_per_cycle) FROM class_subject_hours WHERE tenant_id = ?",
                    Integer.class,
                    dataset.tenantId());
            assertThat(demanded).isEqualTo(300);

            // Class names follow the reconfigured grades and sections.
            String first = jdbcTemplate.queryForObject(
                    "SELECT name FROM school_classes WHERE tenant_id = ? ORDER BY id LIMIT 1",
                    String.class,
                    dataset.tenantId());
            assertThat(first).isEqualTo("1A");
        }
    }

    /** A curriculum that no longer fills the cycle must not seed a subtly broken school. */
    @SpringBootTest
    @ActiveProfiles("demo")
    @TestPropertySource(properties = {
        "app.demo.auto-seed=false",
        // 36 configured periods against a 35-slot cycle.
        "app.demo.cycle.periods-per-day=5",
    })
    static class CurriculumThatDoesNotFillTheCycle {

        @Autowired DemoDataSeeder seeder;

        @Test
        void refusesToSeedAndSaysWhichKnobToTurn() {
            assertThatThrownBySeeding(seeder)
                    .hasMessageContaining("demands 36 periods per class")
                    .hasMessageContaining("the cycle offers 30")
                    .hasMessageContaining("periods-per-cycle");
        }
    }

    /** Shrinking a teacher pool past the workload cap is the other easy mistake. */
    @SpringBootTest
    @ActiveProfiles("demo")
    @TestPropertySource(properties = {
        "app.demo.auto-seed=false",
        // One teacher carrying the entire 36-period week for all 21 classes = 756 periods.
        // The curriculum still fills the cycle exactly, so this reaches the workload check.
        "app.demo.subjects[0].code=ALL",
        "app.demo.subjects[0].name=Everything",
        "app.demo.subjects[0].periods-per-cycle=36",
        "app.demo.subjects[0].teachers=1",
        "app.demo.subjects[0].color=#3B82F6",
        "app.demo.subjects[0].difficulty=3",
        "app.demo.subjects[0].spread-pattern=ANY",
        "app.demo.subjects[0].max-per-day=6",
    })
    static class TeacherPoolBelowTheWorkloadCap {

        @Autowired DemoDataSeeder seeder;

        @Test
        void refusesToSeedAndSuggestsAPoolSize() {
            assertThatThrownBySeeding(seeder)
                    .hasMessageContaining("ALL")
                    .hasMessageContaining("over the workload cap of 34")
                    .hasMessageContaining("Raise subjects[ALL].teachers to 21");
        }
    }

    /**
     * Overriding one field of a list element replaces the whole list, which is a sharp edge of Spring
     * property binding rather than anything this project chose. It used to surface as a
     * NullPointerException from the curriculum sum; it must name the missing key instead.
     */
    @SpringBootTest
    @ActiveProfiles("demo")
    @TestPropertySource(properties = {
        "app.demo.auto-seed=false",
        "app.demo.subjects[0].teachers=1",
    })
    static class PartiallySpecifiedSubject {

        @Autowired DemoDataSeeder seeder;

        @Test
        void refusesToSeedAndNamesTheMissingKey() {
            assertThatThrownBySeeding(seeder)
                    .hasMessageContaining("app.demo.subjects[0]")
                    .hasMessageContaining("is missing 'code'")
                    .hasMessageContaining("replaces the whole list");
        }
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable>
            assertThatThrownBySeeding(DemoDataSeeder seeder) {
        return org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> seeder.seed("invalid-" + UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }
}
