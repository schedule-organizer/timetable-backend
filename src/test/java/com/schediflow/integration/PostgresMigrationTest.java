package com.schediflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the schema the migrations actually produce.
 *
 * <p>The whole suite now runs on PostgreSQL via Testcontainers, so every test is implicitly a check
 * that the migrations applied. This one checks the things that would otherwise go unnoticed: that all
 * 35 applied rather than some silently failing, and that the column types the application relies on
 * are the types it thinks they are.</p>
 *
 * <p>That last point is not hypothetical. Two {@code jsonb} columns were mapped as {@code varchar}
 * and nothing caught it, because the suite ran on H2 in compatibility mode where the difference does
 * not exist — while registration and custom templates were broken on the real database.</p>
 */
@SpringBootTest
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
