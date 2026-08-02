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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DelegationRequestEndpointTest extends AbstractEndpointTest {

    private static final String DELEGATION_URL = "/api/v1/delegation";
    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 9, 7);

    private String adminToken;
    private String requesterToken;
    private String otherTeacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long subjectId;
    private long classId;
    private long requesterUserId;
    private long targetTeacherId;
    private long requesterTeacherId;
    private List<Long> periods;
    private long timetableId;
    private long lessonId;
    private long secondLessonId;
    private long otherTeachersLessonId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@deleg-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        String requesterEmail = "req+" + UUID.randomUUID() + "@deleg-test.edu";
        requesterUserId = inviteAndGetUserId(adminToken, requesterEmail);
        requesterToken = loginAndGetToken(requesterEmail, PASSWORD);

        String targetEmail = "target+" + UUID.randomUUID() + "@deleg-test.edu";
        long targetUserId = inviteAndGetUserId(adminToken, targetEmail);
        otherTeacherToken = loginAndGetToken(targetEmail, PASSWORD);

        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-deleg.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        classId = createClass(adminToken, "8A");
        periods = periodIds(adminToken);

        requesterTeacherId = createTeacherProfile(adminToken, requesterUserId, "Requester");
        targetTeacherId = createTeacherProfile(adminToken, targetUserId, "Target");

        long termId = createTerm(adminToken, LESSON_DATE.minusMonths(1), LESSON_DATE.plusMonths(1));
        timetableId = insertTimetable(tenantId, termId, defaultBellScheduleId(adminToken), "PUBLISHED");
        lessonId = insertLesson(
                tenantId, timetableId, subjectId, classId, requesterUserId, periods.get(0), LESSON_DATE);
        secondLessonId = insertLesson(
                tenantId, timetableId, subjectId, classId, requesterUserId, periods.get(1), LESSON_DATE);
        otherTeachersLessonId = insertLesson(
                tenantId, timetableId, subjectId, classId, targetUserId, periods.get(2), LESSON_DATE);
    }

    @Test
    void post_handover_createsPendingRequest() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(lessonId), targetTeacherId, "Conference")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("HANDOVER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedByUserId").value(requesterUserId))
                .andExpect(jsonPath("$.targetTeacherId").value(targetTeacherId))
                .andExpect(jsonPath("$.lessonIds.length()").value(1))
                .andExpect(jsonPath("$.reason").value("Conference"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.decidedAt").doesNotExist());
    }

    @Test
    void post_swap_withMultipleLessons_createsPendingRequest() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("swap", List.of(lessonId, secondLessonId), targetTeacherId, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SWAP"))
                .andExpect(jsonPath("$.lessonIds.length()").value(2));

        Integer links = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delegation_request_lessons WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(links).isEqualTo(2);
    }

    @Test
    void post_unknownType_returns400() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("GIVEAWAY", List.of(lessonId), targetTeacherId, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_emptyLessonIds_returns400() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(), targetTeacherId, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_duplicateLessonIds_returns400() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(lessonId, lessonId), targetTeacherId, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_delegatingToYourself_returns400() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(lessonId), requesterTeacherId, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_anotherTeachersLesson_returns403() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(otherTeachersLessonId), targetTeacherId, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_mixOfOwnAndOthersLessons_returns403() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "HANDOVER", List.of(lessonId, otherTeachersLessonId), targetTeacherId, null)))
                .andExpect(status().isForbidden());

        Integer requests = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delegation_requests WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(requests).isZero();
    }

    @Test
    void post_unknownLesson_returns404() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(999_999_999L), targetTeacherId, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_unknownTargetTeacher_returns404() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(lessonId), 999_999_999L, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_crossTenant_returns404() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(lessonId), targetTeacherId, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_lessonAlreadyInAPendingRequest_returns409() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(lessonId), targetTeacherId, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SWAP", List.of(lessonId, secondLessonId), targetTeacherId, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void post_asAnotherTeacher_cannotDelegateRequestersLessons() throws Exception {
        // The target teacher tries to hand the requester's lesson over to the requester.
        // Not self-delegation, so this reaches — and fails — the ownership check.
        mockMvc.perform(post(DELEGATION_URL)
                        .header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(lessonId), requesterTeacherId, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(DELEGATION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HANDOVER", List.of(lessonId), targetTeacherId, null)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String body(String type, List<Long> lessonIds, Long targetTeacher, String reason) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("lessonIds", lessonIds);
        payload.put("targetTeacherId", targetTeacher);
        payload.put("reason", reason);
        return objectMapper.writeValueAsString(payload);
    }
}
