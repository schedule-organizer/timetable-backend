package com.schediflow.service;

import com.schediflow.config.DemoDataProperties;
import com.schediflow.domain.InstitutionTemplate;
import com.schediflow.repository.InstitutionTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the arithmetic of the demo fixture.
 *
 * <p>The fixture only earns its keep if its numbers are exactly right: SCHED-17 asserts "756 lessons"
 * against it, and a curriculum that quietly summed to 35, or a teacher pool that quietly overloaded
 * one teacher, would turn a generation bug and a fixture bug into the same failure. Each assertion
 * here pins one of the claims the story makes.</p>
 *
 * <p>Runs on the {@code demo} profile for its configuration, with auto-seeding off — this class seeds
 * explicitly, once per test, under a unique slug.</p>
 */
@SpringBootTest
@ActiveProfiles("demo")
@TestPropertySource(properties = "app.demo.auto-seed=false")
class DemoDataSeederTest {

    @Autowired DemoDataSeeder seeder;
    @Autowired DemoDataProperties properties;
    @Autowired InstitutionTemplateRepository templateRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private DemoDataSeeder.DemoDataset dataset;

    @BeforeEach
    void seed() {
        dataset = seeder.seed("demo-" + UUID.randomUUID());
    }

    @Test
    void shippedConfigurationDescribesTheSchoolTheStoryClaims() {
        // Pins application-demo.yml itself. If someone retunes the fixture, this is the test that
        // tells them SCHED-17's "756" needs retuning too.
        assertThat(properties.classCount()).isEqualTo(21);
        assertThat(properties.slotsPerClass()).isEqualTo(36);
        assertThat(properties.teacherCount()).isEqualTo(30);
        assertThat(properties.subjects()).hasSize(11);
        assertThat(properties.expectedLessonsPerCycle()).isEqualTo(756);
    }

    @Test
    void seedsTheConfiguredShape() {
        assertThat(dataset.classIds()).hasSize(properties.classCount());
        assertThat(dataset.teacherUserIds()).hasSize(properties.teacherCount());
        assertThat(dataset.subjectIdsByCode()).hasSize(properties.subjects().size());
        assertThat(dataset.teachingPeriodIds()).hasSize(properties.cycle().periodsPerDay());
        assertThat(dataset.roomIds()).hasSize(
                properties.classCount()
                        + properties.rooms().labs()
                        + properties.rooms().gyms()
                        + properties.rooms().studios());
    }

    @Test
    void curriculumFillsEveryClassCycleExactly() {
        List<Map<String, Object>> perClass = jdbcTemplate.queryForList(
                "SELECT class_id, SUM(periods_per_cycle) AS total FROM class_subject_hours "
                        + "WHERE tenant_id = ? GROUP BY class_id",
                dataset.tenantId());

        assertThat(perClass).hasSize(properties.classCount());
        assertThat(perClass)
                .as("every class must demand exactly the slots its cycle offers")
                .allSatisfy(row ->
                        assertThat(((Number) row.get("total")).intValue())
                                .isEqualTo(properties.slotsPerClass()));
    }

    @Test
    void totalCurriculumDemandMatchesTheLessonCountTheStoryClaims() {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT SUM(periods_per_cycle) FROM class_subject_hours WHERE tenant_id = ?",
                Integer.class,
                dataset.tenantId());

        assertThat(total).isEqualTo(dataset.expectedLessonsPerCycle());
    }

    @Test
    void everyClassSubjectPairIsStaffedByExactlyOneActiveGroup() {
        // SCHED-17 returns 422 on an unstaffed pair, so the fixture must have none — otherwise the
        // happy-path test would never reach generation.
        Integer unstaffed = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM class_subject_hours csh
                WHERE csh.tenant_id = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM teaching_groups tg
                      JOIN teaching_group_classes tgc ON tgc.teaching_group_id = tg.id
                      WHERE tg.tenant_id = csh.tenant_id
                        AND tg.subject_id = csh.subject_id
                        AND tgc.class_id  = csh.class_id
                        AND tg.is_active  = TRUE)
                """,
                Integer.class,
                dataset.tenantId());

        assertThat(unstaffed).isZero();

        Integer groups = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teaching_groups WHERE tenant_id = ?",
                Integer.class,
                dataset.tenantId());
        assertThat(groups).isEqualTo(properties.classCount() * properties.subjects().size());
    }

    @Test
    void noTeacherIsAllocatedBeyondTheirWorkloadCap() {
        // Load a teacher would carry if every allocation of every class they teach is materialised.
        List<Map<String, Object>> loads = jdbcTemplate.queryForList(
                """
                SELECT tg.teacher_id, SUM(csh.periods_per_cycle) AS load
                FROM teaching_groups tg
                JOIN teaching_group_classes tgc ON tgc.teaching_group_id = tg.id
                JOIN class_subject_hours csh
                     ON csh.class_id = tgc.class_id AND csh.subject_id = tg.subject_id
                WHERE tg.tenant_id = ?
                GROUP BY tg.teacher_id
                """,
                dataset.tenantId());

        assertThat(loads).hasSize(properties.teacherCount());
        assertThat(loads).allSatisfy(row ->
                assertThat(((Number) row.get("load")).intValue())
                        .as("teacher %s load", row.get("teacher_id"))
                        .isLessThanOrEqualTo(properties.teachers().workloadCap()));

        int total = loads.stream().mapToInt(r -> ((Number) r.get("load")).intValue()).sum();
        assertThat(total)
                .as("every curriculum period is staffed")
                .isEqualTo(dataset.expectedLessonsPerCycle());
    }

    @Test
    void teacherUtilisationIsTightEnoughToBeWorthSolving() {
        // ~70% of the week. A loose fixture would let a solver with no clash constraints look
        // correct; this is the property that makes the fixture a real test.
        double mean = (double) dataset.expectedLessonsPerCycle() / properties.teacherCount();
        assertThat(mean / properties.slotsPerClass()).isBetween(0.65, 0.75);
    }

    @Test
    void lunchIsNotAPlaceablePeriod() {
        Integer teaching = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_periods "
                        + "WHERE bell_schedule_id = ? AND is_break = FALSE AND is_lunch = FALSE",
                Integer.class,
                dataset.bellScheduleId());
        Integer all = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_periods WHERE bell_schedule_id = ?",
                Integer.class,
                dataset.bellScheduleId());

        assertThat(teaching).isEqualTo(properties.cycle().periodsPerDay());
        assertThat(all).isEqualTo(properties.cycle().periodsPerDay() + 1);
    }

    @Test
    void everyClassHasItsOwnHomeroom() {
        // SCHED-17 assigns homerooms to non-specialist lessons; that is only clash-free because the
        // mapping is one-to-one.
        Integer distinct = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT homeroom_id) FROM school_classes WHERE tenant_id = ?",
                Integer.class,
                dataset.tenantId());

        assertThat(distinct).isEqualTo(properties.classCount());
    }

    @Test
    void tenantSettingsRoundTripAsJsonb() {
        // The seeder writes settings as a JSON string into a jsonb column. That only works because
        // Tenant.settings carries @JdbcTypeCode(SqlTypes.JSON); without it PostgreSQL rejects the
        // bind outright, which is how registration came to be broken on the real database while
        // this suite — then running on H2 — stayed green.
        String timezone = jdbcTemplate.queryForObject(
                "SELECT settings ->> 'timezone' FROM tenants WHERE id = ?",
                String.class,
                dataset.tenantId());

        assertThat(timezone).isEqualTo(properties.tenant().timezone());
    }

    @Test
    void customTemplatesPersistTheirJsonbConfiguration() {
        // Same defect, same fix, different table: TMPL-04 writes institution_templates
        // .configuration_json, declared jsonb in V033 and mapped as text until it was corrected.
        InstitutionTemplate template = new InstitutionTemplate();
        template.setTenantId(dataset.tenantId());
        template.setName("Round-trip probe");
        template.setInstitutionType("SECONDARY");
        template.setConfigurationJson("{\"cycleDays\":6,\"periodsPerDay\":6}");
        template.setBuiltIn(false);
        Long id = templateRepository.save(template).getId();

        Integer cycleDays = jdbcTemplate.queryForObject(
                "SELECT (configuration_json ->> 'cycleDays')::int FROM institution_templates "
                        + "WHERE id = ?",
                Integer.class,
                id);

        assertThat(cycleDays).isEqualTo(6);
    }

    @Test
    void seedsNoLessons() {
        // The whole point: generation is SCHED-17's job, and the fixture must leave it undone.
        Integer lessons = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lessons WHERE tenant_id = ?", Integer.class, dataset.tenantId());

        assertThat(lessons).isZero();
    }
}
