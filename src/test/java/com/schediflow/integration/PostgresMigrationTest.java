package com.schediflow.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real migrations against real PostgreSQL.
 *
 * <p>Everything else in this suite runs on H2 in PostgreSQL <em>compatibility mode</em>, which is an
 * approximation. That approximation has already shaped the schema: partial unique indexes for active
 * room names, active subject codes, and one ACTIVE temporary schedule per timetable were all left to
 * service-layer enforcement because H2 cannot express them. Until this test existed, no migration had
 * ever been executed against the database the product actually ships on.</p>
 *
 * <p>Skips itself when PostgreSQL is not reachable, so {@code ./mvnw test} on a laptop with no Docker
 * behaves as before. To run it:</p>
 *
 * <pre>
 *   docker compose up -d postgres
 *   ./mvnw test -Dtest=PostgresMigrationTest
 * </pre>
 *
 * <p>The condition is evaluated before the Spring context is built, which is why it is
 * {@code @EnabledIf} on a static probe rather than an assumption in {@code @BeforeAll}.</p>
 */
@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIf("com.schediflow.integration.PostgresAvailability#reachable")
class PostgresMigrationTest {

    private static final int EXPECTED_MIGRATIONS = 35;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void everyMigrationApplied() {
        List<Map<String, Object>> failed = jdbcTemplate.queryForList(
                "SELECT version, description FROM flyway_schema_history WHERE success = false");
        assertThat(failed).as("failed migrations").isEmpty();

        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version IS NOT NULL",
                Integer.class);
        assertThat(applied)
                .as("all %d migrations applied against PostgreSQL", EXPECTED_MIGRATIONS)
                .isEqualTo(EXPECTED_MIGRATIONS);
    }

    @Test
    void schemaHasTheTablesTheApplicationExpects() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "tenants", "users", "academic_years", "terms", "bell_schedules", "schedule_periods",
                "rooms", "subjects", "school_classes", "teachers", "teacher_qualifications",
                "teaching_groups", "class_subject_hours", "lessons", "timetables", "solver_jobs",
                "cover_assignments", "delegation_requests", "temporary_schedules", "audit_log",
                "institution_templates");
    }

    @Test
    void jsonbColumnsAreReallyJsonb() {
        // H2's compat mode accepts JSONB and stores something else. If the real type had drifted to
        // text, every settings query would still pass on H2 and fail here.
        String type = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'tenants' AND column_name = 'settings'",
                String.class);

        assertThat(type).isEqualTo("jsonb");
    }

    @Test
    void identityColumnsGenerateKeys() {
        // BIGSERIAL behaves differently enough between H2 compat mode and PostgreSQL to be worth one
        // explicit round trip.
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO tenants (slug, name, status, settings) "
                        + "VALUES (?, 'Migration probe', 'ACTIVE', '{}'::jsonb) RETURNING id",
                Long.class,
                "migration-probe-" + System.nanoTime());

        assertThat(id).isNotNull().isPositive();
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", id);
    }

    @Test
    void checkConstraintsSurvivedTheTranslation() {
        // V026 restricts timetable status, V035 renamed MOD to MODERATOR. Both are CHECK constraints
        // written for PostgreSQL but only ever executed on H2.
        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE contype = 'c'", String.class);

        assertThat(constraints).contains("chk_timetables_status", "chk_teaching_groups_type");
    }
}
