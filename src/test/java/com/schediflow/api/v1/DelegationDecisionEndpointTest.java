package com.schediflow.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DelegationDecisionEndpointTest extends AbstractEndpointTest {

    private static final String DELEGATION_URL = "/api/v1/delegation";
    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 9, 7);

    private String adminToken;
    private String modToken;
    private String requesterToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long subjectId;
    private long classId;
    private long requesterUserId;
    private long targetUserId;
    private long targetTeacherId;
    private List<Long> periods;
    private long timetableId;
    private long lessonId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@decide-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        modToken = createModUser(adminToken, "mod+" + UUID.randomUUID() + "@decide-test.edu");

        String requesterEmail = "req+" + UUID.randomUUID() + "@decide-test.edu";
        requesterUserId = inviteAndGetUserId(adminToken, requesterEmail);
        requesterToken = loginAndGetToken(requesterEmail, PASSWORD);

        String targetEmail = "target+" + UUID.randomUUID() + "@decide-test.edu";
        targetUserId = inviteAndGetUserId(adminToken, targetEmail);

        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-decide.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        classId = createClass(adminToken, "8A");
        periods = periodIds(adminToken);

        createTeacherProfile(adminToken, requesterUserId, "Requester");
        targetTeacherId = createTeacherProfile(adminToken, targetUserId, "Target");

        long termId = createTerm(adminToken, LESSON_DATE.minusMonths(1), LESSON_DATE.plusMonths(1));
        timetableId = insertTimetable(tenantId, termId, defaultBellScheduleId(adminToken), "PUBLISHED");
        lessonId = insertLesson(
                tenantId, timetableId, subjectId, classId, requesterUserId, periods.get(0), LESSON_DATE);
    }

    @Test
    void patch_approveHandover_reassignsLessonToTarget() throws Exception {
        long requestId = submit("HANDOVER", List.of(lessonId));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("APPROVED", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedAt").exists())
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        assertThat(teacherOf(lessonId)).isEqualTo(targetUserId);
    }

    @Test
    void patch_approveSwap_exchangesLessonsInTheSameSlot() throws Exception {
        long targetsLesson = insertLesson(
                tenantId, timetableId, subjectId, classId, targetUserId, periods.get(0), LESSON_DATE);
        long requestId = submit("SWAP", List.of(lessonId));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("APPROVED", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(teacherOf(lessonId)).isEqualTo(targetUserId);
        assertThat(teacherOf(targetsLesson)).isEqualTo(requesterUserId);
    }

    @Test
    void patch_reject_leavesLessonsUntouched() throws Exception {
        long requestId = submit("HANDOVER", List.of(lessonId));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("REJECTED", "Too short notice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Too short notice"));

        assertThat(teacherOf(lessonId)).isEqualTo(requesterUserId);
    }

    @Test
    void patch_rejectWithoutReason_returns400() throws Exception {
        long requestId = submit("HANDOVER", List.of(lessonId));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("REJECTED", "   ")))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(requestId)).isEqualTo("PENDING");
    }

    @Test
    void patch_unknownDecision_returns400() throws Exception {
        long requestId = submit("HANDOVER", List.of(lessonId));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("MAYBE", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_alreadyDecided_returns400() throws Exception {
        long requestId = submit("HANDOVER", List.of(lessonId));
        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("APPROVED", null)))
                .andExpect(status().isOk());

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("REJECTED", "changed my mind")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_approvalThatWouldDoubleBook_returns409AndChangesNothing() throws Exception {
        // The target already teaches in the same slot, so a HANDOVER would clash.
        long targetsLesson = insertLesson(
                tenantId, timetableId, subjectId, classId, targetUserId, periods.get(0), LESSON_DATE);
        long requestId = submit("HANDOVER", List.of(lessonId));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("APPROVED", null)))
                .andExpect(status().isConflict());

        assertThat(teacherOf(lessonId)).isEqualTo(requesterUserId);
        assertThat(teacherOf(targetsLesson)).isEqualTo(targetUserId);
        assertThat(statusOf(requestId)).isEqualTo("PENDING");
    }

    @Test
    void patch_approveMultipleLessons_reassignsAllOfThem() throws Exception {
        long second = insertLesson(
                tenantId, timetableId, subjectId, classId, requesterUserId, periods.get(1), LESSON_DATE);
        long requestId = submit("HANDOVER", List.of(lessonId, second));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("APPROVED", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonIds.length()").value(2));

        assertThat(teacherOf(lessonId)).isEqualTo(targetUserId);
        assertThat(teacherOf(second)).isEqualTo(targetUserId);
    }

    @Test
    void patch_unknownRequest_returns404() throws Exception {
        mockMvc.perform(patch(DELEGATION_URL + "/999999999")
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("APPROVED", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_crossTenant_returns404() throws Exception {
        long requestId = submit("HANDOVER", List.of(lessonId));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("APPROVED", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_asRequestingTeacher_returns403() throws Exception {
        long requestId = submit("HANDOVER", List.of(lessonId));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("APPROVED", null)))
                .andExpect(status().isForbidden());

        assertThat(statusOf(requestId)).isEqualTo("PENDING");
    }

    @Test
    void patch_withoutToken_returns401() throws Exception {
        long requestId = submit("HANDOVER", List.of(lessonId));

        mockMvc.perform(patch(DELEGATION_URL + "/" + requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision("APPROVED", null)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private long submit(String type, List<Long> lessonIds) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("lessonIds", lessonIds);
        payload.put("targetTeacherId", targetTeacherId);
        return createdId(mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String decision(String decision, String rejectionReason) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("decision", decision);
        payload.put("rejectionReason", rejectionReason);
        return objectMapper.writeValueAsString(payload);
    }

    private long teacherOf(long lesson) {
        return jdbcTemplate.queryForObject(
                "SELECT teacher_user_id FROM lessons WHERE id = ?", Long.class, lesson);
    }

    private String statusOf(long requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM delegation_requests WHERE id = ?", String.class, requestId);
    }
}
