package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.sql.Date;
import java.sql.Time;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
class TeacherEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @SpyBean EmailService emailService;

    private static final String TEACHERS_URL = "/api/v1/teachers";
    private static final String PASSWORD = "Password1";

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;
    private long teacherUserId;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@teacher-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Teacher School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@teacher-test.edu";
        modToken = createModUser("mod+" + UUID.randomUUID() + "@teacher-test.edu");
        createTeacherUser(teacherEmail);

        teacherUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, teacherEmail);

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-teacher.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other Teacher School " + UUID.randomUUID(),
                                "email", otherEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);

        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);
    }

    @Test
    void post_asAdmin_createsTeacher_returns201() throws Exception {
        mockMvc.perform(post(TEACHERS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherBody(teacherUserId, "Ms Smith"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(teacherUserId))
                .andExpect(jsonPath("$.displayName").value("Ms Smith"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void post_asMod_createsTeacher_returns201() throws Exception {
        String email = "t2+" + UUID.randomUUID() + "@teacher-test.edu";
        long uid = inviteAndCompleteUser(email);

        mockMvc.perform(post(TEACHERS_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherBody(uid, "Mr Jones"))))
                .andExpect(status().isCreated());
    }

    @Test
    void post_duplicateUser_returns409() throws Exception {
        createTeacherProfile(teacherUserId, "First");

        mockMvc.perform(post(TEACHERS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherBody(teacherUserId, "Second"))))
                .andExpect(status().isConflict());
    }

    @Test
    void post_unknownUserId_returns404() throws Exception {
        mockMvc.perform(post(TEACHERS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherBody(999_999_999L, "Nobody"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getList_asTeacher_returns200() throws Exception {
        createTeacherProfile(teacherUserId, "Listed");

        mockMvc.perform(get(TEACHERS_URL).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Listed"));
    }

    @Test
    void getById_crossTenant_returns404() throws Exception {
        long id = createTeacherProfile(teacherUserId, "X");

        mockMvc.perform(get(TEACHERS_URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_updatesTeacher_returns200() throws Exception {
        long id = createTeacherProfile(teacherUserId, "Old");

        mockMvc.perform(put(TEACHERS_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherBody(teacherUserId, "New Name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"));
    }

    @Test
    void delete_softDeletes_returns204() throws Exception {
        long id = createTeacherProfile(teacherUserId, "To Remove");

        mockMvc.perform(delete(TEACHERS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(TEACHERS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_whenLessonExists_returns409() throws Exception {
        long id = createTeacherProfile(teacherUserId, "Busy");
        insertMinimalLessonForTeacher(teacherUserId);

        mockMvc.perform(delete(TEACHERS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(TEACHERS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherBody(teacherUserId, "Nope"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long id = createTeacherProfile(teacherUserId, "P");

        mockMvc.perform(put(TEACHERS_URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherBody(teacherUserId, "Q"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long id = createTeacherProfile(teacherUserId, "R");

        mockMvc.perform(delete(TEACHERS_URL + "/" + id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    private Map<String, Object> teacherBody(long userId, String displayName) {
        return Map.of(
                "userId", userId,
                "displayName", displayName,
                "maxPeriodsPerDay", 6,
                "maxConsecutivePeriods", 3,
                "workloadCap", 24);
    }

    private long createTeacherProfile(long userId, String displayName) throws Exception {
        MvcResult r = mockMvc.perform(post(TEACHERS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherBody(userId, displayName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private void insertMinimalLessonForTeacher(long teacherUserId) {
        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM users WHERE id = ?", Long.class, teacherUserId);

        jdbcTemplate.update(
                "INSERT INTO academic_years (tenant_id, name, start_date, end_date, is_active) VALUES (?, ?, ?, ?, ?)",
                tenantId, "AY T Guard", Date.valueOf("2025-01-01"), Date.valueOf("2025-12-31"), true);
        Long ayId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM academic_years WHERE tenant_id = ?", Long.class, tenantId);
        jdbcTemplate.update(
                "INSERT INTO terms (tenant_id, academic_year_id, name, ordinal, start_date, end_date) VALUES (?, ?, ?, ?, ?, ?)",
                tenantId, ayId, "T1", 1, Date.valueOf("2025-01-01"), Date.valueOf("2025-06-30"));
        Long termId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM terms WHERE tenant_id = ?", Long.class, tenantId);
        jdbcTemplate.update(
                "INSERT INTO bell_schedules (tenant_id, name, is_default) VALUES (?, ?, ?)",
                tenantId, "Bell T", false);
        Long bellId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM bell_schedules WHERE tenant_id = ?", Long.class, tenantId);
        jdbcTemplate.update(
                "INSERT INTO schedule_periods (bell_schedule_id, tenant_id, name, start_time, end_time, ordinal) VALUES (?, ?, ?, ?, ?, ?)",
                bellId, tenantId, "P1", Time.valueOf("09:00:00"), Time.valueOf("10:00:00"), 1);
        Long periodId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM schedule_periods WHERE tenant_id = ?", Long.class, tenantId);
        jdbcTemplate.update(
                "INSERT INTO timetables (tenant_id, term_id, bell_schedule_id, name, status) VALUES (?, ?, ?, ?, ?)",
                tenantId, termId, bellId, "TT T Guard", "DRAFT");
        Long ttId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM timetables WHERE tenant_id = ?", Long.class, tenantId);

        jdbcTemplate.update(
                "INSERT INTO school_classes (tenant_id, name, year_level, is_active) VALUES (?, ?, ?, ?)",
                tenantId, "Class T", 7, true);
        Long classId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM school_classes WHERE tenant_id = ?", Long.class, tenantId);

        String code = "SUBT" + teacherUserId;
        jdbcTemplate.update(
                "INSERT INTO subjects (tenant_id, name, code, color, spread_pattern) VALUES (?, ?, ?, ?, ?)",
                tenantId, "Subj T", code, "#001122", "ANY");
        Long subjectId = jdbcTemplate.queryForObject(
                "SELECT id FROM subjects WHERE tenant_id = ? AND code = ?", Long.class, tenantId, code);

        jdbcTemplate.update(
                """
                        INSERT INTO lessons (tenant_id, timetable_id, subject_id, class_id, teacher_user_id, schedule_period_id, scheduled_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                tenantId, ttId, subjectId, classId, teacherUserId, periodId, Date.valueOf("2025-03-15"));
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createModUser(String email) throws Exception {
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvitation(eq(email), urlCaptor.capture());
        String rawToken = urlCaptor.getValue();
        rawToken = rawToken.substring(rawToken.indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken, "password", PASSWORD))))
                .andExpect(status().isOk());

        MvcResult usersResult = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode users =
                objectMapper.readTree(usersResult.getResponse().getContentAsString()).get("content");
        Long userId = null;
        for (com.fasterxml.jackson.databind.JsonNode user : users) {
            if (email.equals(user.get("email").asText())) {
                userId = user.get("id").asLong();
                break;
            }
        }

        mockMvc.perform(put("/api/v1/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "MODERATOR"))))
                .andExpect(status().isOk());

        return loginAndGetToken(email, PASSWORD);
    }

    private void createTeacherUser(String email) throws Exception {
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvitation(eq(email), urlCaptor.capture());
        String rawToken = urlCaptor.getValue();
        rawToken = rawToken.substring(rawToken.indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken, "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    private long inviteAndCompleteUser(String email) throws Exception {
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvitation(eq(email), urlCaptor.capture());
        String rawToken = urlCaptor.getValue();
        rawToken = rawToken.substring(rawToken.indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken, "password", PASSWORD))))
                .andExpect(status().isOk());

        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}
