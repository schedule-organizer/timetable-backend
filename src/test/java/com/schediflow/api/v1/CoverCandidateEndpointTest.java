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

class CoverCandidateEndpointTest extends AbstractEndpointTest {

    private static final String CANDIDATES_URL = "/api/v1/cover/candidates";
    /** A Monday. */
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
    private List<Long> periods;
    private long timetableId;
    private long lessonId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@cand-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        modToken = createModUser(adminToken, "mod+" + UUID.randomUUID() + "@cand-test.edu");

        String originalEmail = "orig+" + UUID.randomUUID() + "@cand-test.edu";
        originalTeacherUserId = inviteAndGetUserId(adminToken, originalEmail);
        teacherToken = loginAndGetToken(originalEmail, PASSWORD);

        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-cand.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        otherSubjectId = createSubject(adminToken, "Art", "ART");
        classId = createClass(adminToken, "8A");
        periods = periodIds(adminToken);

        long originalTeacherId = createTeacherProfile(adminToken, originalTeacherUserId, "Original Teacher");
        qualify(adminToken, originalTeacherId, subjectId);

        long termId = createTerm(adminToken, LESSON_DATE.minusMonths(1), LESSON_DATE.plusMonths(1));
        timetableId = insertTimetable(tenantId, termId, defaultBellScheduleId(adminToken), "PUBLISHED");
        lessonId = insertLesson(
                tenantId, timetableId, subjectId, classId, originalTeacherUserId, periods.get(0), LESSON_DATE);
    }

    @Test
    void get_returnsQualifiedAvailableTeachers() throws Exception {
        Candidate ann = newTeacher("Ann", subjectId, 24);

        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherId").value(ann.teacherId))
                .andExpect(jsonPath("$[0].displayName").value("Ann"))
                .andExpect(jsonPath("$[0].qualifications[0]").value(subjectId))
                .andExpect(jsonPath("$[0].currentWorkload").value(0))
                .andExpect(jsonPath("$[0].workloadCap").value(24))
                .andExpect(jsonPath("$[0].workloadGap").value(24));
    }

    @Test
    void get_excludesTheLessonsOwnTeacher() throws Exception {
        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void get_excludesTeachersQualifiedForOtherSubjectsOnly() throws Exception {
        newTeacher("Wrong Subject", otherSubjectId, 24);

        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void get_excludesTeachersBusyInThatPeriod() throws Exception {
        Candidate busy = newTeacher("Busy", subjectId, 24);
        insertLesson(tenantId, timetableId, subjectId, classId, busy.userId, periods.get(0), LESSON_DATE);

        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void get_excludesTeachersWithAForbiddenSlotInThatPeriod() throws Exception {
        Candidate blocked = newTeacher("Blocked", subjectId, 24);
        Map<String, Object> slot = new HashMap<>();
        slot.put("entityType", "TEACHER");
        slot.put("entityId", blocked.teacherId);
        slot.put("dayOfWeek", LESSON_DATE.getDayOfWeek().getValue());
        slot.put("periodId", periods.get(0));
        slot.put("isRecurring", true);
        mockMvc.perform(post("/api/v1/forbidden-slots")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slot)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void get_countsTaughtLessonsTowardWorkload() throws Exception {
        Candidate loaded = newTeacher("Loaded", subjectId, 24);
        insertLesson(tenantId, timetableId, subjectId, classId, loaded.userId, periods.get(1), LESSON_DATE);
        insertLesson(tenantId, timetableId, subjectId, classId, loaded.userId, periods.get(2), LESSON_DATE);

        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentWorkload").value(2))
                .andExpect(jsonPath("$[0].workloadGap").value(22));
    }

    @Test
    void get_sortsByWorkloadGapDescending() throws Exception {
        Candidate light = newTeacher("Light", subjectId, 24);
        Candidate heavy = newTeacher("Heavy", subjectId, 24);
        for (int i = 1; i <= 3; i++) {
            insertLesson(tenantId, timetableId, subjectId, classId, heavy.userId, periods.get(i), LESSON_DATE);
        }

        MvcResult result = mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json(result);
        assertThat(body).hasSize(2);
        assertThat(body.get(0).get("teacherId").asLong()).isEqualTo(light.teacherId);
        assertThat(body.get(0).get("workloadGap").asInt()).isEqualTo(24);
        assertThat(body.get(1).get("teacherId").asLong()).isEqualTo(heavy.teacherId);
        assertThat(body.get(1).get("workloadGap").asInt()).isEqualTo(21);
    }

    @Test
    void get_uncappedTeacherSortsFirstWithNullGap() throws Exception {
        newTeacher("Capped", subjectId, 24);
        Candidate uncapped = newTeacherWithoutCap("Uncapped", subjectId);

        MvcResult result = mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json(result);
        assertThat(body).hasSize(2);
        assertThat(body.get(0).get("teacherId").asLong()).isEqualTo(uncapped.teacherId);
        assertThat(body.get(0).get("workloadCap").isNull()).isTrue();
        assertThat(body.get(0).get("workloadGap").isNull()).isTrue();
    }

    @Test
    void get_noQualifiedTeachers_returnsEmptyList() throws Exception {
        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void get_unknownLesson_returns404() throws Exception {
        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("lessonId", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_crossTenantLesson_returns404() throws Exception {
        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_missingLessonId_returns400() throws Exception {
        mockMvc.perform(get(CANDIDATES_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_asTeacher_returns403() throws Exception {
        mockMvc.perform(get(CANDIDATES_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(CANDIDATES_URL).param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private record Candidate(long teacherId, long userId) {}

    private Candidate newTeacher(String name, long subject, int workloadCap) throws Exception {
        String email = name.replace(' ', '.').toLowerCase() + "+" + UUID.randomUUID() + "@cand-test.edu";
        long userId = inviteAndGetUserId(adminToken, email);
        long teacherId = createTeacherProfile(adminToken, userId, name, workloadCap);
        qualify(adminToken, teacherId, subject);
        return new Candidate(teacherId, userId);
    }

    private Candidate newTeacherWithoutCap(String name, long subject) throws Exception {
        String email = name.replace(' ', '.').toLowerCase() + "+" + UUID.randomUUID() + "@cand-test.edu";
        long userId = inviteAndGetUserId(adminToken, email);
        long teacherId = createdId(postCreated("/api/v1/teachers", adminToken, Map.of(
                "userId", userId, "displayName", name)));
        qualify(adminToken, teacherId, subject);
        return new Candidate(teacherId, userId);
    }
}
