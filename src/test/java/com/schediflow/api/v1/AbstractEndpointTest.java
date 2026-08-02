package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.service.EmailService;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for endpoint tests: registration, invitation, role promotion, and the resource
 * scaffolding Epic 7 needs (terms, timetables, lessons).
 *
 * <p>Timetables and lessons have no write API yet — SCHED-01/02 will add one — so they are inserted
 * directly. Everything else goes through the real endpoints.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
abstract class AbstractEndpointTest {

    protected static final String PASSWORD = "Password1";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @SpyBean protected EmailService emailService;

    // ---------- identity ----------

    /** Registers a new institution and returns its ADMIN access token. */
    protected String registerAdmin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Institution " + UUID.randomUUID(),
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        return loginAndGetToken(email, PASSWORD);
    }

    protected String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    /** Invites a user, completes their registration, and returns their access token. */
    protected String inviteTeacher(String adminToken, String email) throws Exception {
        inviteAndComplete(adminToken, email);
        return loginAndGetToken(email, PASSWORD);
    }

    protected long inviteAndGetUserId(String adminToken, String email) throws Exception {
        inviteAndComplete(adminToken, email);
        return userIdOf(email);
    }

    /** Invites a user, promotes them to MOD, and returns their access token. */
    protected String createModUser(String adminToken, String email) throws Exception {
        inviteAndComplete(adminToken, email);
        mockMvc.perform(put("/api/v1/users/" + userIdOf(email) + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "MOD"))))
                .andExpect(status().isOk());
        return loginAndGetToken(email, PASSWORD);
    }

    private void inviteAndComplete(String adminToken, String email) throws Exception {
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvitation(eq(email), urlCaptor.capture());
        String url = urlCaptor.getValue();
        String rawToken = url.substring(url.indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken, "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    protected long userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    protected long tenantIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM users WHERE email = ?", Long.class, email);
    }

    // ---------- resources ----------

    protected long createTeacherProfile(String adminToken, long userId, String displayName) throws Exception {
        return createdId(postCreated("/api/v1/teachers", adminToken, Map.of(
                "userId", userId,
                "displayName", displayName,
                "maxPeriodsPerDay", 6,
                "maxConsecutivePeriods", 3,
                "workloadCap", 24)));
    }

    protected long createTeacherProfile(
            String adminToken, long userId, String displayName, int workloadCap) throws Exception {
        return createdId(postCreated("/api/v1/teachers", adminToken, Map.of(
                "userId", userId,
                "displayName", displayName,
                "maxPeriodsPerDay", 6,
                "maxConsecutivePeriods", 3,
                "workloadCap", workloadCap)));
    }

    protected long createSubject(String adminToken, String name, String code) throws Exception {
        return createdId(postCreated("/api/v1/subjects", adminToken, Map.of(
                "name", name,
                "code", code,
                "color", "#123456",
                "difficultyLevel", 3,
                "requiredRoomType", "CLASSROOM",
                "maxPerDay", 2,
                "spreadPattern", "SPREAD")));
    }

    protected long createClass(String adminToken, String name) throws Exception {
        return createdId(postCreated("/api/v1/classes", adminToken, Map.of(
                "name", name, "yearLevel", 8, "capacity", 30)));
    }

    protected void qualify(String adminToken, long teacherId, long subjectId) throws Exception {
        mockMvc.perform(post("/api/v1/teachers/" + teacherId + "/qualifications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId))))
                .andExpect(status().isCreated());
    }

    /** Ids of the seeded default bell schedule's periods, in ordinal order. */
    protected List<Long> periodIds(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/bell-schedules").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode periods = json(result).get(0).get("periods");
        return periods.findValues("id").stream().map(JsonNode::asLong).toList();
    }

    protected long defaultBellScheduleId(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/bell-schedules").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get(0).get("id").asLong();
    }

    protected long activeAcademicYearId(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/academic-years").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get(0).get("id").asLong();
    }

    protected long createTerm(String adminToken, LocalDate start, LocalDate end) throws Exception {
        return createdId(postCreated("/api/v1/terms", adminToken, Map.of(
                "academicYearId", activeAcademicYearId(adminToken),
                "name", "Term " + UUID.randomUUID(),
                "ordinal", 1,
                "startDate", start.toString(),
                "endDate", end.toString())));
    }

    // ---------- direct inserts (no write API yet) ----------

    protected long insertTimetable(long tenantId, long termId, long bellScheduleId, String status) {
        jdbcTemplate.update(
                "INSERT INTO timetables (tenant_id, term_id, bell_schedule_id, name, status)"
                        + " VALUES (?, ?, ?, ?, ?)",
                tenantId, termId, bellScheduleId, "Timetable " + UUID.randomUUID(), status);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM timetables WHERE tenant_id = ?", Long.class, tenantId);
    }

    protected long insertLesson(
            long tenantId,
            long timetableId,
            long subjectId,
            long classId,
            long teacherUserId,
            long schedulePeriodId,
            LocalDate scheduledDate) {
        jdbcTemplate.update(
                "INSERT INTO lessons (tenant_id, timetable_id, subject_id, class_id, teacher_user_id,"
                        + " schedule_period_id, scheduled_date) VALUES (?, ?, ?, ?, ?, ?, ?)",
                tenantId, timetableId, subjectId, classId, teacherUserId, schedulePeriodId, scheduledDate);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM lessons WHERE tenant_id = ?", Long.class, tenantId);
    }

    // ---------- plumbing ----------

    protected MvcResult postCreated(String url, String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    protected long createdId(MvcResult result) throws Exception {
        return json(result).get("id").asLong();
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
