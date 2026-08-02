package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCHED-03 (run), SCHED-04 (status/history) and SCHED-05 (cancel). */
class EngineEndpointTest extends AbstractEndpointTest {

    private static final String RUN_URL = "/api/v1/engine/run";
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
        String adminEmail = "admin+" + UUID.randomUUID() + "@engine-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        modToken = createModUser(adminToken, "mod+" + UUID.randomUUID() + "@engine-test.edu");
        String teacherEmail = "t+" + UUID.randomUUID() + "@engine-test.edu";
        teacherUserId = inviteAndGetUserId(adminToken, teacherEmail);
        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);
        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-engine.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        classAId = createClass(adminToken, "8A");
        long teacherId = createTeacherProfile(adminToken, teacherUserId, "Ann");
        qualify(adminToken, teacherId, subjectId);
        periods = periodIds(adminToken);

        long termId = createTerm(adminToken, MONDAY.minusMonths(1), MONDAY.plusMonths(1));
        timetableId = createdId(postCreated("/api/v1/timetables", adminToken, Map.of(
                "name", "TT " + UUID.randomUUID(), "termId", termId)));
    }

    @Test
    void post_run_onEmptyTimetable_completesImmediately() throws Exception {
        mockMvc.perform(run(adminToken, timetableId, "FAST", null))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNumber())
                .andExpect(jsonPath("$.timetableId").value(timetableId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.mode").value("FAST"))
                .andExpect(jsonPath("$.timeoutSeconds").value(30))
                .andExpect(jsonPath("$.hardViolations").value(0));
    }

    @Test
    void post_run_defaultsToBalancedMode() throws Exception {
        mockMvc.perform(run(adminToken, timetableId, null, null))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mode").value("BALANCED"))
                .andExpect(jsonPath("$.timeoutSeconds").value(120));
    }

    @Test
    void post_run_honoursTimeoutOverride() throws Exception {
        mockMvc.perform(run(adminToken, timetableId, "THOROUGH", 45))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.timeoutSeconds").value(45));
    }

    @Test
    void post_run_withLessons_solvesAndCompletes() throws Exception {
        insertLesson(tenantId, timetableId, subjectId, classAId, teacherUserId, periods.get(0), MONDAY);

        MvcResult result = mockMvc.perform(run(adminToken, timetableId, "FAST", 2))
                .andExpect(status().isAccepted())
                .andReturn();
        long jobId = json(result).get("jobId").asLong();

        JsonNode job = awaitTerminal(jobId);
        assertThat(job.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(job.get("hardViolations").asInt()).isZero();
        assertThat(job.get("qualityScore").asText()).contains("hard");
    }

    @Test
    void post_run_leavesPinnedLessonsWhereTheyAre() throws Exception {
        long pinned = insertLesson(
                tenantId, timetableId, subjectId, classAId, teacherUserId, periods.get(3), MONDAY);
        jdbcTemplate.update("UPDATE lessons SET is_pinned = TRUE WHERE id = ?", pinned);

        MvcResult result = mockMvc.perform(run(adminToken, timetableId, "FAST", 2))
                .andExpect(status().isAccepted())
                .andReturn();
        awaitTerminal(json(result).get("jobId").asLong());

        Long period = jdbcTemplate.queryForObject(
                "SELECT schedule_period_id FROM lessons WHERE id = ?", Long.class, pinned);
        assertThat(period).isEqualTo(periods.get(3));
    }

    @Test
    void post_run_unknownMode_returns400() throws Exception {
        mockMvc.perform(run(adminToken, timetableId, "LUDICROUS", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_run_excessiveTimeout_returns400() throws Exception {
        mockMvc.perform(run(adminToken, timetableId, "FAST", 99_999))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_run_unknownTimetable_returns404() throws Exception {
        mockMvc.perform(run(adminToken, 999_999_999L, "FAST", null)).andExpect(status().isNotFound());
    }

    @Test
    void post_run_crossTenant_returns404() throws Exception {
        mockMvc.perform(run(otherTenantAdminToken, timetableId, "FAST", null))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_run_asTeacher_returns403() throws Exception {
        mockMvc.perform(run(teacherToken, timetableId, "FAST", null)).andExpect(status().isForbidden());
    }

    @Test
    void get_job_returnsStatusAndHistory() throws Exception {
        MvcResult result = mockMvc.perform(run(modToken, timetableId, "FAST", null))
                .andExpect(status().isAccepted())
                .andReturn();
        long jobId = json(result).get("jobId").asLong();

        mockMvc.perform(get("/api/v1/engine/jobs/" + jobId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.timetableId").value(timetableId));

        mockMvc.perform(get("/api/v1/engine/jobs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("timetableId", String.valueOf(timetableId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void get_job_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/engine/jobs/999999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_job_crossTenant_returns404() throws Exception {
        MvcResult result = mockMvc.perform(run(adminToken, timetableId, "FAST", null)).andReturn();
        long jobId = json(result).get("jobId").asLong();

        mockMvc.perform(get("/api/v1/engine/jobs/" + jobId)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_run_whileAnotherIsRunning_returns409() throws Exception {
        insertLesson(tenantId, timetableId, subjectId, classAId, teacherUserId, periods.get(0), MONDAY);
        mockMvc.perform(run(adminToken, timetableId, "THOROUGH", 60)).andExpect(status().isAccepted());

        mockMvc.perform(run(adminToken, timetableId, "FAST", null)).andExpect(status().isConflict());
    }

    @Test
    void post_cancel_stopsARunningJob() throws Exception {
        insertLesson(tenantId, timetableId, subjectId, classAId, teacherUserId, periods.get(0), MONDAY);
        MvcResult result = mockMvc.perform(run(adminToken, timetableId, "THOROUGH", 60))
                .andExpect(status().isAccepted())
                .andReturn();
        long jobId = json(result).get("jobId").asLong();

        mockMvc.perform(post("/api/v1/engine/jobs/" + jobId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void post_cancel_alreadyTerminal_returns400() throws Exception {
        MvcResult result = mockMvc.perform(run(adminToken, timetableId, "FAST", null)).andReturn();
        long jobId = json(result).get("jobId").asLong();

        mockMvc.perform(post("/api/v1/engine/jobs/" + jobId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_cancel_unknownJob_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/engine/jobs/999999999/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void engine_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/engine/jobs")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.RequestBuilder run(
            String token, long timetable, String mode, Integer timeoutSeconds) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timetableId", timetable);
        payload.put("mode", mode);
        payload.put("timeoutSeconds", timeoutSeconds);
        return post(RUN_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }

    /** Solving is asynchronous, so poll the job until it settles. */
    private JsonNode awaitTerminal(long jobId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/engine/jobs/" + jobId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode job = json(result);
            String status = job.get("status").asText();
            if (List.of("COMPLETED", "FAILED", "CANCELLED").contains(status)) {
                return job;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Solver job " + jobId + " did not finish in time");
    }
}
