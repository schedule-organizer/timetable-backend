package com.schediflow.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TemporaryScheduleEndpointTest extends AbstractEndpointTest {

    private static final String SCHEDULES_URL = "/api/v1/temporary-schedules";
    private static final LocalDate TERM_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate TERM_END = LocalDate.of(2026, 12, 15);

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long timetableId;
    private long secondTimetableId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@temp-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        modToken = createModUser(adminToken, "mod+" + UUID.randomUUID() + "@temp-test.edu");
        teacherToken = inviteTeacher(adminToken, "teacher+" + UUID.randomUUID() + "@temp-test.edu");
        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-temp.edu");

        long termId = createTerm(adminToken, TERM_START, TERM_END);
        long bellScheduleId = defaultBellScheduleId(adminToken);
        timetableId = insertTimetable(tenantId, termId, bellScheduleId, "PUBLISHED");
        secondTimetableId = insertTimetable(tenantId, termId, bellScheduleId, "DRAFT");
    }

    @Test
    void post_createsActiveOverlay() throws Exception {
        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Exam Week", timetableId, "2026-10-05", "2026-10-09")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Exam Week"))
                .andExpect(jsonPath("$.baseTimetableId").value(timetableId))
                .andExpect(jsonPath("$.startDate").value("2026-10-05"))
                .andExpect(jsonPath("$.endDate").value("2026-10-09"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.overrideCount").value(0));
    }

    @Test
    void post_startDateNotBeforeEndDate_returns400() throws Exception {
        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Backwards", timetableId, "2026-10-09", "2026-10-05")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Same day", timetableId, "2026-10-05", "2026-10-05")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_startsBeforeTheTerm_returns400() throws Exception {
        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Too early", timetableId, "2026-08-20", "2026-09-10")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_endsAfterTheTerm_returns400() throws Exception {
        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Too late", timetableId, "2026-12-01", "2027-01-10")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_spanningTheWholeTerm_isAllowed() throws Exception {
        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Whole term", timetableId, TERM_START.toString(), TERM_END.toString())))
                .andExpect(status().isCreated());
    }

    @Test
    void post_secondActiveOverlayOnSameTimetable_returns409() throws Exception {
        createSchedule("First", timetableId, "2026-10-05", "2026-10-09");

        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Second", timetableId, "2026-11-02", "2026-11-06")))
                .andExpect(status().isConflict());
    }

    @Test
    void post_activeOverlaysOnDifferentTimetables_areAllowed() throws Exception {
        createSchedule("First", timetableId, "2026-10-05", "2026-10-09");

        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Second", secondTimetableId, "2026-10-05", "2026-10-09")))
                .andExpect(status().isCreated());
    }

    @Test
    void post_unknownTimetable_returns404() throws Exception {
        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ghost", 999_999_999L, "2026-10-05", "2026-10-09")))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_updatesNameAndDates() throws Exception {
        long id = createSchedule("Before", timetableId, "2026-10-05", "2026-10-09");

        mockMvc.perform(put(SCHEDULES_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("After", timetableId, "2026-11-02", "2026-11-06")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.startDate").value("2026-11-02"));
    }

    @Test
    void put_doesNotConflictWithItself() throws Exception {
        long id = createSchedule("Only one", timetableId, "2026-10-05", "2026-10-09");

        mockMvc.perform(put(SCHEDULES_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Only one", timetableId, "2026-10-06", "2026-10-10")))
                .andExpect(status().isOk());
    }

    @Test
    void get_listsAndFetchesById() throws Exception {
        long id = createSchedule("Listed", timetableId, "2026-10-05", "2026-10-09");

        mockMvc.perform(get(SCHEDULES_URL).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get(SCHEDULES_URL + "/" + id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Listed"));
    }

    @Test
    void delete_removesOverlayAndItsOverrides() throws Exception {
        long id = createSchedule("Doomed", timetableId, "2026-10-05", "2026-10-09");
        insertOverride(id);

        mockMvc.perform(delete(SCHEDULES_URL + "/" + id).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SCHEDULES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        Integer overrides = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM temporary_schedule_lessons WHERE temporary_schedule_id = ?",
                Integer.class, id);
        assertThat(overrides).isZero();
    }

    @Test
    void delete_thenCreateAgain_isAllowed() throws Exception {
        long id = createSchedule("First", timetableId, "2026-10-05", "2026-10-09");
        mockMvc.perform(delete(SCHEDULES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Replacement", timetableId, "2026-10-05", "2026-10-09")))
                .andExpect(status().isCreated());
    }

    @Test
    void overrideCount_reflectsStoredOverrides() throws Exception {
        long id = createSchedule("With overrides", timetableId, "2026-10-05", "2026-10-09");
        insertOverride(id);

        mockMvc.perform(get(SCHEDULES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrideCount").value(1));
    }

    @Test
    void get_crossTenant_returns404AndEmptyList() throws Exception {
        long id = createSchedule("Mine", timetableId, "2026-10-05", "2026-10-09");

        mockMvc.perform(get(SCHEDULES_URL + "/" + id).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(SCHEDULES_URL).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Nope", timetableId, "2026-10-05", "2026-10-09")))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(SCHEDULES_URL)).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String body(String name, long baseTimetableId, String start, String end) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("baseTimetableId", baseTimetableId);
        payload.put("startDate", start);
        payload.put("endDate", end);
        return objectMapper.writeValueAsString(payload);
    }

    private long createSchedule(String name, long baseTimetableId, String start, String end) throws Exception {
        return createdId(mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, baseTimetableId, start, end)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    /** Overrides have no write API yet (see the story notes), so seed one directly. */
    private void insertOverride(long temporaryScheduleId) throws Exception {
        long subjectId = createSubject(adminToken, "Maths " + UUID.randomUUID(), "M" + shortCode());
        long classId = createClass(adminToken, "Class " + UUID.randomUUID());
        long teacherUserId = inviteAndGetUserId(adminToken, "ovr+" + UUID.randomUUID() + "@temp-test.edu");
        long periodId = periodIds(adminToken).get(0);

        jdbcTemplate.update(
                "INSERT INTO temporary_schedule_lessons (tenant_id, temporary_schedule_id, subject_id,"
                        + " class_id, teacher_user_id, schedule_period_id, scheduled_date)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                tenantId, temporaryScheduleId, subjectId, classId, teacherUserId, periodId,
                LocalDate.of(2026, 10, 6));
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
