package com.schediflow.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimetableEndpointTest extends AbstractEndpointTest {

    private static final String TIMETABLES_URL = "/api/v1/timetables";
    private static final LocalDate TERM_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate TERM_END = LocalDate.of(2026, 12, 15);

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long termId;
    private long bellScheduleId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@tt-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        modToken = createModUser(adminToken, "mod+" + UUID.randomUUID() + "@tt-test.edu");
        teacherToken = inviteTeacher(adminToken, "teacher+" + UUID.randomUUID() + "@tt-test.edu");
        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-tt.edu");

        termId = createTerm(adminToken, TERM_START, TERM_END);
        bellScheduleId = defaultBellScheduleId(adminToken);
    }

    @Test
    void post_createsDraftTimetable() throws Exception {
        mockMvc.perform(post(TIMETABLES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Autumn 2026", termId, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Autumn 2026"))
                .andExpect(jsonPath("$.termId").value(termId))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.bellScheduleId").value(bellScheduleId));
    }

    @Test
    void post_withExplicitBellSchedule_usesIt() throws Exception {
        mockMvc.perform(post(TIMETABLES_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Explicit", termId, bellScheduleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bellScheduleId").value(bellScheduleId));
    }

    @Test
    void post_unknownTerm_returns404() throws Exception {
        mockMvc.perform(post(TIMETABLES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ghost", 999_999_999L, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_unknownBellSchedule_returns404() throws Exception {
        mockMvc.perform(post(TIMETABLES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ghost bells", termId, 999_999_999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchStatus_draftToPublishedToArchived() throws Exception {
        long id = createTimetable("Lifecycle");

        mockMvc.perform(patchStatus(id, adminToken, "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(patchStatus(id, adminToken, "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void patchStatus_draftDirectlyToArchived_isAllowed() throws Exception {
        long id = createTimetable("Abandoned");

        mockMvc.perform(patchStatus(id, adminToken, "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void patchStatus_backwardsTransition_returns400() throws Exception {
        long id = createTimetable("No going back");
        mockMvc.perform(patchStatus(id, adminToken, "PUBLISHED")).andExpect(status().isOk());

        mockMvc.perform(patchStatus(id, adminToken, "DRAFT")).andExpect(status().isBadRequest());

        mockMvc.perform(patchStatus(id, adminToken, "ARCHIVED")).andExpect(status().isOk());
        mockMvc.perform(patchStatus(id, adminToken, "PUBLISHED")).andExpect(status().isBadRequest());
    }

    @Test
    void patchStatus_toSameStatus_returns400() throws Exception {
        long id = createTimetable("Already draft");

        mockMvc.perform(patchStatus(id, adminToken, "DRAFT")).andExpect(status().isBadRequest());
    }

    @Test
    void patchStatus_unknownStatus_returns400() throws Exception {
        long id = createTimetable("Bad status");

        mockMvc.perform(patchStatus(id, adminToken, "RETIRED")).andExpect(status().isBadRequest());
    }

    @Test
    void publishing_archivesThePreviouslyPublishedTimetableForTheTerm() throws Exception {
        long first = createTimetable("First");
        long second = createTimetable("Second");
        mockMvc.perform(patchStatus(first, adminToken, "PUBLISHED")).andExpect(status().isOk());

        mockMvc.perform(patchStatus(second, adminToken, "PUBLISHED")).andExpect(status().isOk());

        assertThat(statusOf(first)).isEqualTo("ARCHIVED");
        assertThat(statusOf(second)).isEqualTo("PUBLISHED");

        Integer published = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM timetables WHERE term_id = ? AND status = 'PUBLISHED'",
                Integer.class, termId);
        assertThat(published).isEqualTo(1);
    }

    @Test
    void get_filtersByTermAndStatus() throws Exception {
        long draft = createTimetable("Draft one");
        long published = createTimetable("Published one");
        mockMvc.perform(patchStatus(published, adminToken, "PUBLISHED")).andExpect(status().isOk());

        mockMvc.perform(get(TIMETABLES_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(draft));

        mockMvc.perform(get(TIMETABLES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("termId", String.valueOf(termId))
                        .param("status", "published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(published));

        mockMvc.perform(get(TIMETABLES_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void get_unknownStatusFilter_returns400() throws Exception {
        mockMvc.perform(get(TIMETABLES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "NONSENSE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_updatesName() throws Exception {
        long id = createTimetable("Before");

        mockMvc.perform(put(TIMETABLES_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("After", termId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"));
    }

    @Test
    void put_onArchivedTimetable_returns409() throws Exception {
        long id = createTimetable("Archived");
        mockMvc.perform(patchStatus(id, adminToken, "ARCHIVED")).andExpect(status().isOk());

        mockMvc.perform(put(TIMETABLES_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Nope", termId, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_draftTimetable_succeeds() throws Exception {
        long id = createTimetable("Doomed");

        mockMvc.perform(delete(TIMETABLES_URL + "/" + id).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(TIMETABLES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_alsoRemovesItsLessons() throws Exception {
        long id = createTimetable("With lessons");
        long subjectId = createSubject(adminToken, "Maths", "MTH");
        long classId = createClass(adminToken, "8A");
        long teacherUserId = inviteAndGetUserId(adminToken, "t+" + UUID.randomUUID() + "@tt-test.edu");
        insertLesson(tenantId, id, subjectId, classId, teacherUserId,
                periodIds(adminToken).get(0), LocalDate.of(2026, 9, 7));

        mockMvc.perform(delete(TIMETABLES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Integer lessons = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lessons WHERE timetable_id = ?", Integer.class, id);
        assertThat(lessons).isZero();
    }

    @Test
    void delete_publishedTimetable_returns409() throws Exception {
        long id = createTimetable("Published");
        mockMvc.perform(patchStatus(id, adminToken, "PUBLISHED")).andExpect(status().isOk());

        mockMvc.perform(delete(TIMETABLES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_archivedTimetable_returns409() throws Exception {
        long id = createTimetable("Archived");
        mockMvc.perform(patchStatus(id, adminToken, "ARCHIVED")).andExpect(status().isOk());

        mockMvc.perform(delete(TIMETABLES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void get_crossTenant_returns404AndEmptyList() throws Exception {
        long id = createTimetable("Mine");

        mockMvc.perform(get(TIMETABLES_URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(TIMETABLES_URL).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void writes_asTeacher_return403() throws Exception {
        long id = createTimetable("Protected");

        mockMvc.perform(post(TIMETABLES_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Nope", termId, null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patchStatus(id, teacherToken, "PUBLISHED")).andExpect(status().isForbidden());

        mockMvc.perform(delete(TIMETABLES_URL + "/" + id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(TIMETABLES_URL)).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String body(String name, Long term, Long bellSchedule) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("termId", term);
        payload.put("bellScheduleId", bellSchedule);
        return objectMapper.writeValueAsString(payload);
    }

    private long createTimetable(String name) throws Exception {
        return createdId(mockMvc.perform(post(TIMETABLES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, termId, null)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private org.springframework.test.web.servlet.RequestBuilder patchStatus(
            long id, String token, String status) throws Exception {
        return patch(TIMETABLES_URL + "/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", status)));
    }

    private String statusOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM timetables WHERE id = ?", String.class, id);
    }
}
