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

class CoverAssignmentEndpointTest extends AbstractEndpointTest {

    private static final String COVER_URL = "/api/v1/cover";
    /** A Monday, so recurring forbidden slots with dayOfWeek = 1 line up with it. */
    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 9, 7);

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long subjectId;
    private long otherSubjectId;
    private long classId;
    private long originalTeacherUserId;
    private long coverTeacherId;
    private long coverTeacherUserId;
    private List<Long> periods;
    private long timetableId;
    private long lessonId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@cover-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        modToken = createModUser(adminToken, "mod+" + UUID.randomUUID() + "@cover-test.edu");

        String originalEmail = "orig+" + UUID.randomUUID() + "@cover-test.edu";
        originalTeacherUserId = inviteAndGetUserId(adminToken, originalEmail);

        String coverEmail = "cover+" + UUID.randomUUID() + "@cover-test.edu";
        coverTeacherUserId = inviteAndGetUserId(adminToken, coverEmail);
        teacherToken = loginAndGetToken(coverEmail, PASSWORD);

        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-cover.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        otherSubjectId = createSubject(adminToken, "Art", "ART");
        classId = createClass(adminToken, "8A");
        periods = periodIds(adminToken);

        createTeacherProfile(adminToken, originalTeacherUserId, "Original Teacher");
        coverTeacherId = createTeacherProfile(adminToken, coverTeacherUserId, "Cover Teacher");
        qualify(adminToken, coverTeacherId, subjectId);

        long termId = createTerm(adminToken, LESSON_DATE.minusMonths(1), LESSON_DATE.plusMonths(1));
        timetableId = insertTimetable(tenantId, termId, defaultBellScheduleId(adminToken), "PUBLISHED");
        lessonId = insertLesson(
                tenantId, timetableId, subjectId, classId, originalTeacherUserId, periods.get(0), LESSON_DATE);
    }

    @Test
    void post_asAdmin_assignsCover_returns201() throws Exception {
        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, "Illness")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.lessonId").value(lessonId))
                .andExpect(jsonPath("$.coverTeacherId").value(coverTeacherId))
                .andExpect(jsonPath("$.originalTeacherUserId").value(originalTeacherUserId))
                .andExpect(jsonPath("$.reason").value("Illness"))
                .andExpect(jsonPath("$.assignedAt").exists());
    }

    @Test
    void post_leavesTheLessonsOwnTeacherOnRecord() throws Exception {
        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isCreated());

        Long stillOriginal = jdbcTemplate.queryForObject(
                "SELECT teacher_user_id FROM lessons WHERE id = ?", Long.class, lessonId);
        assertThat(stillOriginal).isEqualTo(originalTeacherUserId);

        Long persisted = jdbcTemplate.queryForObject(
                "SELECT original_teacher_user_id FROM cover_assignments WHERE lesson_id = ?",
                Long.class, lessonId);
        assertThat(persisted).isEqualTo(originalTeacherUserId);
    }

    @Test
    void post_withoutReason_returns201() throws Exception {
        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isCreated());
    }

    @Test
    void post_unqualifiedTeacher_returns400() throws Exception {
        long unqualified = newTeacher("unqual", otherSubjectId);

        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, unqualified, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_lessonsOwnTeacherAsCover_returns400() throws Exception {
        long originalTeacherId = jdbcTemplate.queryForObject(
                "SELECT id FROM teachers WHERE user_id = ?", Long.class, originalTeacherUserId);
        qualify(adminToken, originalTeacherId, subjectId);

        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, originalTeacherId, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_teacherAlreadyTeachingInThatPeriod_returns409() throws Exception {
        insertLesson(tenantId, timetableId, subjectId, classId, coverTeacherUserId, periods.get(0), LESSON_DATE);

        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void post_teacherTeachingInAnotherPeriod_isAllowed() throws Exception {
        insertLesson(tenantId, timetableId, subjectId, classId, coverTeacherUserId, periods.get(1), LESSON_DATE);

        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isCreated());
    }

    @Test
    void post_teacherWithRecurringForbiddenSlot_returns409() throws Exception {
        Map<String, Object> slot = new HashMap<>();
        slot.put("entityType", "TEACHER");
        slot.put("entityId", coverTeacherId);
        slot.put("dayOfWeek", LESSON_DATE.getDayOfWeek().getValue());
        slot.put("periodId", periods.get(0));
        slot.put("isRecurring", true);
        mockMvc.perform(post("/api/v1/forbidden-slots")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slot)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void post_teacherWithOneOffForbiddenSlotOnThatDate_returns409() throws Exception {
        Map<String, Object> slot = new HashMap<>();
        slot.put("entityType", "TEACHER");
        slot.put("entityId", coverTeacherId);
        slot.put("specificDate", LESSON_DATE.toString());
        slot.put("periodId", periods.get(0));
        slot.put("isRecurring", false);
        mockMvc.perform(post("/api/v1/forbidden-slots")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slot)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void post_twice_returns409() throws Exception {
        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isCreated());

        long second = newTeacher("second", subjectId);
        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, second, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void post_unknownLesson_returns404() throws Exception {
        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999_999_999L, coverTeacherId, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_unknownTeacher_returns404() throws Exception {
        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, 999_999_999L, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_crossTenantLesson_returns404() throws Exception {
        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_missingLessonId_returns400() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("coverTeacherId", coverTeacherId);

        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(COVER_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(COVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(lessonId, coverTeacherId, null)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String body(long lesson, long coverTeacher, String reason) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lessonId", lesson);
        payload.put("coverTeacherId", coverTeacher);
        payload.put("reason", reason);
        return objectMapper.writeValueAsString(payload);
    }

    /** Creates an extra teacher profile qualified for {@code subjectId}. */
    private long newTeacher(String prefix, long subject) throws Exception {
        long userId = inviteAndGetUserId(adminToken, prefix + "+" + UUID.randomUUID() + "@cover-test.edu");
        long teacherId = createTeacherProfile(adminToken, userId, prefix + " Teacher");
        qualify(adminToken, teacherId, subject);
        return teacherId;
    }
}
