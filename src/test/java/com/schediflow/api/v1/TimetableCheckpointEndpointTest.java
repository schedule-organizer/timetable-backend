package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCHED-13 checkpoints (FR26/FR27). Retention is lowered so the limit is reachable in a test. */
@TestPropertySource(properties = {"app.ratelimit.max-requests=500", "app.timetables.max-checkpoints=3"})
class TimetableCheckpointEndpointTest extends AbstractEndpointTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long timetableId;
    private long subjectId;
    private long classAId;
    private long teacherUserId;
    private List<Long> periods;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@cp-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);
        modToken = createModUser(adminToken, "mod+" + UUID.randomUUID() + "@cp-test.edu");
        teacherToken = inviteTeacher(adminToken, "t+" + UUID.randomUUID() + "@cp-test.edu");
        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-cp.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        classAId = createClass(adminToken, "8A");
        teacherUserId = inviteAndGetUserId(adminToken, "lt+" + UUID.randomUUID() + "@cp-test.edu");
        periods = periodIds(adminToken);

        long termId = createTerm(adminToken, MONDAY.minusMonths(1), MONDAY.plusMonths(1));
        timetableId = createdId(postCreated("/api/v1/timetables", adminToken, Map.of(
                "name", "TT " + UUID.randomUUID(), "termId", termId)));
    }

    @Test
    void post_createsCheckpointOfCurrentLessons() throws Exception {
        lesson(periods.get(0));
        lesson(periods.get(1));

        mockMvc.perform(create(adminToken, "Before exams"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Before exams"))
                .andExpect(jsonPath("$.lessonCount").value(2))
                .andExpect(jsonPath("$.timetableId").value(timetableId));
    }

    @Test
    void restore_returnsTheTimetableToItsCheckpointedState() throws Exception {
        long lessonId = lesson(periods.get(0));
        long checkpointId = createdId(mockMvc.perform(create(adminToken, "Good state"))
                .andExpect(status().isCreated()).andReturn());

        // Mutate: move the lesson and add another.
        mockMvc.perform(patch("/api/v1/lessons/" + lessonId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("periodId", periods.get(4)))))
                .andExpect(status().isOk());
        lesson(periods.get(5));
        assertThat(lessonCount()).isEqualTo(2);

        mockMvc.perform(post(url() + "/" + checkpointId + "/restore")
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(checkpointId));

        assertThat(lessonCount()).isEqualTo(1);
        Long period = jdbcTemplate.queryForObject(
                "SELECT schedule_period_id FROM lessons WHERE timetable_id = ?", Long.class, timetableId);
        assertThat(period).isEqualTo(periods.get(0));
    }

    @Test
    void restore_isIdempotent() throws Exception {
        lesson(periods.get(0));
        long checkpointId = createdId(mockMvc.perform(create(adminToken, "Stable"))
                .andExpect(status().isCreated()).andReturn());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(url() + "/" + checkpointId + "/restore")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
        assertThat(lessonCount()).isEqualTo(1);
    }

    @Test
    void restore_preservesPinnedFlag() throws Exception {
        long lessonId = lesson(periods.get(0));
        jdbcTemplate.update("UPDATE lessons SET is_pinned = TRUE WHERE id = ?", lessonId);
        long checkpointId = createdId(mockMvc.perform(create(adminToken, "Pinned"))
                .andExpect(status().isCreated()).andReturn());

        jdbcTemplate.update("UPDATE lessons SET is_pinned = FALSE WHERE id = ?", lessonId);
        mockMvc.perform(post(url() + "/" + checkpointId + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Boolean pinned = jdbcTemplate.queryForObject(
                "SELECT is_pinned FROM lessons WHERE timetable_id = ?", Boolean.class, timetableId);
        assertThat(pinned).isTrue();
    }

    @Test
    void restore_onPublishedTimetable_returns409() throws Exception {
        lesson(periods.get(0));
        long checkpointId = createdId(mockMvc.perform(create(adminToken, "Snap"))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/timetables/" + timetableId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(url() + "/" + checkpointId + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void get_listsNewestFirst() throws Exception {
        createdId(mockMvc.perform(create(adminToken, "First")).andReturn());
        createdId(mockMvc.perform(create(adminToken, "Second")).andReturn());

        MvcResult result = mockMvc.perform(get(url()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = json(result).get("content");
        assertThat(content.get(0).get("name").asText()).isEqualTo("Second");
        assertThat(content.get(1).get("name").asText()).isEqualTo("First");
    }

    @Test
    void retention_dropsTheOldestBeyondTheLimit() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(create(adminToken, "Checkpoint " + i)).andExpect(status().isCreated());
        }

        MvcResult result = mockMvc.perform(get(url()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json(result);
        assertThat(body.get("totalElements").asInt()).isEqualTo(3);
        assertThat(body.get("content").get(0).get("name").asText()).isEqualTo("Checkpoint 5");
        assertThat(body.get("content").get(2).get("name").asText()).isEqualTo("Checkpoint 3");
    }

    @Test
    void post_unknownTimetable_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/timetables/999999999/checkpoints")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Ghost"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void restore_unknownCheckpoint_returns404() throws Exception {
        mockMvc.perform(post(url() + "/999999999/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenant_returns404() throws Exception {
        mockMvc.perform(get(url()).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void asTeacher_returns403() throws Exception {
        mockMvc.perform(create(teacherToken, "Nope")).andExpect(status().isForbidden());
        mockMvc.perform(get(url()).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get(url())).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String url() {
        return "/api/v1/timetables/" + timetableId + "/checkpoints";
    }

    private org.springframework.test.web.servlet.RequestBuilder create(String token, String name)
            throws Exception {
        return post(url())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name)));
    }

    private long lesson(long periodId) {
        return insertLesson(tenantId, timetableId, subjectId, classAId, teacherUserId, periodId, MONDAY);
    }

    private int lessonCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lessons WHERE timetable_id = ?", Integer.class, timetableId);
    }
}
