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

/** SCHED-14 targeted partial regeneration (FR24). */
class PartialRegenerationEndpointTest extends AbstractEndpointTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    private String adminToken;
    private String teacherToken;

    private long tenantId;
    private long timetableId;
    private long subjectId;
    private long inScopeClassId;
    private long outOfScopeClassId;
    private long teacherAUserId;
    private long teacherBUserId;
    private List<Long> periods;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@scope-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);
        teacherToken = inviteTeacher(adminToken, "t+" + UUID.randomUUID() + "@scope-test.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        inScopeClassId = createClass(adminToken, "8A");
        outOfScopeClassId = createClass(adminToken, "8B");

        teacherAUserId = inviteAndGetUserId(adminToken, "ta+" + UUID.randomUUID() + "@scope-test.edu");
        teacherBUserId = inviteAndGetUserId(adminToken, "tb+" + UUID.randomUUID() + "@scope-test.edu");
        long teacherA = createTeacherProfile(adminToken, teacherAUserId, "Ann");
        long teacherB = createTeacherProfile(adminToken, teacherBUserId, "Ben");
        qualify(adminToken, teacherA, subjectId);
        qualify(adminToken, teacherB, subjectId);
        periods = periodIds(adminToken);

        long termId = createTerm(adminToken, MONDAY.minusMonths(1), MONDAY.plusMonths(1));
        timetableId = createdId(postCreated("/api/v1/timetables", adminToken, Map.of(
                "name", "TT " + UUID.randomUUID(), "termId", termId)));
    }

    @Test
    void run_withClassScope_reportsEligibleAndFrozenCounts() throws Exception {
        lesson(inScopeClassId, teacherAUserId, periods.get(0));
        lesson(outOfScopeClassId, teacherBUserId, periods.get(1));

        MvcResult result = mockMvc.perform(run(adminToken, scope("classIds", List.of(inScopeClassId)), 2))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eligibleLessons").value(1))
                .andExpect(jsonPath("$.frozenLessons").value(1))
                .andExpect(jsonPath("$.scopeDescription").value(
                        org.hamcrest.Matchers.containsString("classes")))
                .andReturn();

        awaitTerminal(json(result).get("jobId").asLong());
    }

    @Test
    void run_leavesOutOfScopeLessonsExactlyWhereTheyWere() throws Exception {
        lesson(inScopeClassId, teacherAUserId, periods.get(0));
        long untouchable = lesson(outOfScopeClassId, teacherBUserId, periods.get(6));

        MvcResult result = mockMvc.perform(run(adminToken, scope("classIds", List.of(inScopeClassId)), 3))
                .andExpect(status().isAccepted())
                .andReturn();
        awaitTerminal(json(result).get("jobId").asLong());

        Long period = jdbcTemplate.queryForObject(
                "SELECT schedule_period_id FROM lessons WHERE id = ?", Long.class, untouchable);
        assertThat(period).isEqualTo(periods.get(6));
    }

    @Test
    void run_withTeacherScope_freezesEveryoneElse() throws Exception {
        lesson(inScopeClassId, teacherAUserId, periods.get(0));
        lesson(outOfScopeClassId, teacherBUserId, periods.get(1));

        mockMvc.perform(run(adminToken, scope("teacherIds", List.of(teacherAUserId)), 2))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eligibleLessons").value(1))
                .andExpect(jsonPath("$.frozenLessons").value(1));
    }

    @Test
    void run_withoutScope_leavesEverythingMovable() throws Exception {
        lesson(inScopeClassId, teacherAUserId, periods.get(0));
        lesson(outOfScopeClassId, teacherBUserId, periods.get(1));

        mockMvc.perform(run(adminToken, null, 2))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eligibleLessons").value(2))
                .andExpect(jsonPath("$.frozenLessons").value(0))
                .andExpect(jsonPath("$.scopeDescription").doesNotExist());
    }

    @Test
    void run_scopeCombinesWithManualPins() throws Exception {
        long pinned = lesson(inScopeClassId, teacherAUserId, periods.get(0));
        jdbcTemplate.update("UPDATE lessons SET is_pinned = TRUE WHERE id = ?", pinned);
        lesson(inScopeClassId, teacherAUserId, periods.get(1));

        mockMvc.perform(run(adminToken, scope("classIds", List.of(inScopeClassId)), 2))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eligibleLessons").value(1))
                .andExpect(jsonPath("$.frozenLessons").value(1));
    }

    @Test
    void run_withEmptyScope_returns400() throws Exception {
        lesson(inScopeClassId, teacherAUserId, periods.get(0));
        Map<String, Object> emptyScope = new HashMap<>();
        emptyScope.put("teacherIds", List.of());
        emptyScope.put("classIds", List.of());

        mockMvc.perform(run(adminToken, emptyScope, 2)).andExpect(status().isBadRequest());
    }

    @Test
    void run_scopeMatchingNothing_freezesEverything() throws Exception {
        lesson(inScopeClassId, teacherAUserId, periods.get(0));

        mockMvc.perform(run(adminToken, scope("classIds", List.of(999_999_999L)), 2))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eligibleLessons").value(0))
                .andExpect(jsonPath("$.frozenLessons").value(1));
    }

    @Test
    void run_scopedAsTeacher_returns403() throws Exception {
        mockMvc.perform(run(teacherToken, scope("classIds", List.of(inScopeClassId)), 2))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private Map<String, Object> scope(String dimension, List<Long> ids) {
        Map<String, Object> scope = new HashMap<>();
        scope.put(dimension, ids);
        return scope;
    }

    private org.springframework.test.web.servlet.RequestBuilder run(
            String token, Map<String, Object> scope, int timeoutSeconds) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timetableId", timetableId);
        payload.put("mode", "FAST");
        payload.put("timeoutSeconds", timeoutSeconds);
        payload.put("scope", scope);
        return post("/api/v1/engine/run")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }

    private long lesson(long classId, long teacherUserId, long periodId) {
        return insertLesson(tenantId, timetableId, subjectId, classId, teacherUserId, periodId, MONDAY);
    }

    private void awaitTerminal(long jobId) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/engine/jobs/" + jobId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode job = json(result);
            if (List.of("COMPLETED", "FAILED", "CANCELLED").contains(job.get("status").asText())) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Solver job " + jobId + " did not finish in time");
    }
}
