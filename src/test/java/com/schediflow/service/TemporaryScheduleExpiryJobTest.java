package com.schediflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the expiry sweep against the real schema. Overlays are inserted directly so past end
 * dates can be created without back-dating a term.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TemporaryScheduleExpiryJobTest {

    @Autowired TemporaryScheduleExpiryJob job;
    @Autowired JdbcTemplate jdbcTemplate;

    private long tenantId;
    private long otherTenantId;
    private long timetableId;
    private long otherTimetableId;

    @BeforeEach
    void setUp() {
        // This job is deliberately cross-tenant, and the in-memory H2 instance outlives each Spring
        // context, so leftovers from earlier tests would make the sweep counts non-deterministic.
        jdbcTemplate.update("DELETE FROM temporary_schedule_lessons");
        jdbcTemplate.update("DELETE FROM temporary_schedules");

        tenantId = insertTenant();
        otherTenantId = insertTenant();
        timetableId = insertTimetable(tenantId);
        otherTimetableId = insertTimetable(otherTenantId);
    }

    @Test
    void expiresOverlaysWhoseEndDateHasPassed() {
        long elapsed = insertSchedule(tenantId, timetableId, LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1), "ACTIVE");

        assertThat(job.expireElapsedSchedules()).isEqualTo(1);
        assertThat(statusOf(elapsed)).isEqualTo("EXPIRED");
    }

    @Test
    void leavesOverlaysStillInForceAlone() {
        long current = insertSchedule(tenantId, timetableId, LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(5), "ACTIVE");

        assertThat(job.expireElapsedSchedules()).isZero();
        assertThat(statusOf(current)).isEqualTo("ACTIVE");
    }

    @Test
    void doesNotExpireAnOverlayEndingToday() {
        long endingToday = insertSchedule(tenantId, timetableId, LocalDate.now().minusDays(3),
                LocalDate.now(), "ACTIVE");

        assertThat(job.expireElapsedSchedules()).isZero();
        assertThat(statusOf(endingToday)).isEqualTo("ACTIVE");
    }

    @Test
    void clearsLessonOverridesSoTheBaseTimetableResumes() {
        long elapsed = insertSchedule(tenantId, timetableId, LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1), "ACTIVE");
        insertOverride(tenantId, elapsed);
        insertOverride(tenantId, elapsed);
        assertThat(overrideCount(elapsed)).isEqualTo(2);

        job.expireElapsedSchedules();

        assertThat(overrideCount(elapsed)).isZero();
    }

    @Test
    void isIdempotent() {
        long elapsed = insertSchedule(tenantId, timetableId, LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1), "ACTIVE");

        assertThat(job.expireElapsedSchedules()).isEqualTo(1);
        assertThat(job.expireElapsedSchedules()).isZero();
        assertThat(job.expireElapsedSchedules()).isZero();
        assertThat(statusOf(elapsed)).isEqualTo("EXPIRED");
    }

    @Test
    void sweepsEveryTenant() {
        long mine = insertSchedule(tenantId, timetableId, LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1), "ACTIVE");
        long theirs = insertSchedule(otherTenantId, otherTimetableId, LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1), "ACTIVE");

        assertThat(job.expireElapsedSchedules()).isEqualTo(2);
        assertThat(statusOf(mine)).isEqualTo("EXPIRED");
        assertThat(statusOf(theirs)).isEqualTo("EXPIRED");
    }

    @Test
    void ignoresAlreadyExpiredOverlays() {
        insertSchedule(tenantId, timetableId, LocalDate.now().minusDays(20),
                LocalDate.now().minusDays(10), "EXPIRED");

        assertThat(job.expireElapsedSchedules()).isZero();
    }

    @Test
    void expiringFreesTheTimetableForANewActiveOverlay() {
        insertSchedule(tenantId, timetableId, LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(1), "ACTIVE");
        job.expireElapsedSchedules();

        Integer active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM temporary_schedules WHERE base_timetable_id = ? AND status = 'ACTIVE'",
                Integer.class, timetableId);
        assertThat(active).isZero();
    }

    // ---------- fixture ----------

    private long insertTenant() {
        String slug = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update("INSERT INTO tenants (name, slug) VALUES (?, ?)", "Tenant " + slug, slug);
        return jdbcTemplate.queryForObject("SELECT id FROM tenants WHERE slug = ?", Long.class, slug);
    }

    private long insertTimetable(long tenant) {
        jdbcTemplate.update(
                "INSERT INTO academic_years (tenant_id, name, start_date, end_date, is_active)"
                        + " VALUES (?, ?, ?, ?, ?)",
                tenant, "Year " + UUID.randomUUID(), LocalDate.now().minusMonths(6),
                LocalDate.now().plusMonths(6), false);
        long yearId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM academic_years WHERE tenant_id = ?", Long.class, tenant);

        jdbcTemplate.update(
                "INSERT INTO terms (tenant_id, academic_year_id, name, ordinal, start_date, end_date)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                tenant, yearId, "Term " + UUID.randomUUID(), 1, LocalDate.now().minusMonths(3),
                LocalDate.now().plusMonths(3));
        long termId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM terms WHERE tenant_id = ?", Long.class, tenant);

        jdbcTemplate.update(
                "INSERT INTO bell_schedules (tenant_id, name, is_default) VALUES (?, ?, ?)",
                tenant, "Bells " + UUID.randomUUID(), true);
        long bellId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM bell_schedules WHERE tenant_id = ?", Long.class, tenant);

        jdbcTemplate.update(
                "INSERT INTO timetables (tenant_id, term_id, bell_schedule_id, name, status)"
                        + " VALUES (?, ?, ?, ?, ?)",
                tenant, termId, bellId, "Timetable " + UUID.randomUUID(), "PUBLISHED");
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM timetables WHERE tenant_id = ?", Long.class, tenant);
    }

    private long insertSchedule(long tenant, long timetable, LocalDate start, LocalDate end, String status) {
        jdbcTemplate.update(
                "INSERT INTO temporary_schedules (tenant_id, base_timetable_id, name, start_date, end_date,"
                        + " status) VALUES (?, ?, ?, ?, ?, ?)",
                tenant, timetable, "Overlay " + UUID.randomUUID(), start, end, status);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM temporary_schedules WHERE tenant_id = ?", Long.class, tenant);
    }

    private void insertOverride(long tenant, long scheduleId) {
        jdbcTemplate.update(
                "INSERT INTO subjects (tenant_id, name, code, color, spread_pattern)"
                        + " VALUES (?, ?, ?, ?, ?)",
                tenant, "Subject " + UUID.randomUUID(), UUID.randomUUID().toString().substring(0, 8),
                "#123456", "ANY");
        long subjectId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM subjects WHERE tenant_id = ?", Long.class, tenant);

        jdbcTemplate.update(
                "INSERT INTO school_classes (tenant_id, name) VALUES (?, ?)",
                tenant, "Class " + UUID.randomUUID());
        long classId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM school_classes WHERE tenant_id = ?", Long.class, tenant);

        String email = "u" + UUID.randomUUID() + "@expiry-test.edu";
        jdbcTemplate.update(
                "INSERT INTO users (tenant_id, email, password_hash, role, status) VALUES (?, ?, ?, ?, ?)",
                tenant, email, "x", "TEACHER", "ACTIVE");
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);

        long bellId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM bell_schedules WHERE tenant_id = ?", Long.class, tenant);
        jdbcTemplate.update(
                "INSERT INTO schedule_periods (bell_schedule_id, tenant_id, name, start_time, end_time,"
                        + " is_break, is_lunch, ordinal) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                bellId, tenant, "P" + UUID.randomUUID().toString().substring(0, 4),
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(9, 45), false, false, nextOrdinal(bellId));
        long periodId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM schedule_periods WHERE tenant_id = ?", Long.class, tenant);

        jdbcTemplate.update(
                "INSERT INTO temporary_schedule_lessons (tenant_id, temporary_schedule_id, subject_id,"
                        + " class_id, teacher_user_id, schedule_period_id, scheduled_date)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                tenant, scheduleId, subjectId, classId, userId, periodId, LocalDate.now().minusDays(5));
    }

    private int nextOrdinal(long bellScheduleId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ordinal), 0) FROM schedule_periods WHERE bell_schedule_id = ?",
                Integer.class, bellScheduleId);
        return max + 1;
    }

    private String statusOf(long scheduleId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM temporary_schedules WHERE id = ?", String.class, scheduleId);
    }

    private int overrideCount(long scheduleId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM temporary_schedule_lessons WHERE temporary_schedule_id = ?",
                Integer.class, scheduleId);
    }
}
