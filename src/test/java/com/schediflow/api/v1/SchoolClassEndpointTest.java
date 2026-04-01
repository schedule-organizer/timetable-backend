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
class SchoolClassEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @SpyBean EmailService emailService;

    private static final String CLASSES_URL = "/api/v1/classes";
    private static final String PASSWORD = "Password1";

    private String adminToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@class-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Class School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        teacherToken = createTeacherUser("teacher+" + UUID.randomUUID() + "@class-test.edu");

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-class.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other Class School " + UUID.randomUUID(),
                                "email", otherEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);
    }

    // ── Happy path ──

    @Test
    void post_asAdmin_createsClass_returns201() throws Exception {
        mockMvc.perform(post(CLASSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody("Year 7A", 7, null, 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Year 7A"))
                .andExpect(jsonPath("$.yearLevel").value(7))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void post_withHomeroom_createsClass_returns201() throws Exception {
        long roomId = createRoom("Room 101");

        mockMvc.perform(post(CLASSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody("Year 8B", 8, roomId, 28))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.homeroomId").value(roomId));
    }

    @Test
    void getList_asTeacher_returns200() throws Exception {
        createClass("Year 7A");

        mockMvc.perform(get(CLASSES_URL).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getById_returns200() throws Exception {
        long id = createClass("Year 7A");

        mockMvc.perform(get(CLASSES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Year 7A"));
    }

    @Test
    void put_updatesClass_returns200() throws Exception {
        long id = createClass("Year 7A");

        mockMvc.perform(put(CLASSES_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody("Year 7B", 7, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Year 7B"));
    }

    @Test
    void delete_softDelete_returns204() throws Exception {
        long id = createClass("Year 7A");

        mockMvc.perform(delete(CLASSES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_softDelete_allowsReuseOfName() throws Exception {
        long id = createClass("Year 7A");

        mockMvc.perform(delete(CLASSES_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(CLASSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody("Year 7A", 7, null, null))))
                .andExpect(status().isCreated());
    }

    // ── Conflict / error cases ──

    @Test
    void post_duplicateName_returns409() throws Exception {
        createClass("Year 7A");

        mockMvc.perform(post(CLASSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody("Year 7A", 7, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void put_duplicateName_returns409() throws Exception {
        createClass("Year 7A");
        long id = createClass("Year 8B");

        mockMvc.perform(put(CLASSES_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody("Year 7A", 8, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void post_withInvalidHomeroom_returns400() throws Exception {
        mockMvc.perform(post(CLASSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody("Year 9C", 9, 99999L, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_whenClassSubjectHoursExist_returns409() throws Exception {
        long classId = createClass("Year 7A");
        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM school_classes WHERE id = ?", Long.class, classId);
        String uniqueCode = "TMP" + classId;
        jdbcTemplate.update(
                "INSERT INTO subjects (tenant_id, name, code, color, spread_pattern) VALUES (?, ?, ?, ?, ?)",
                tenantId, "Temp Subject", uniqueCode, "#AABBCC", "ANY");
        Long subjectId = jdbcTemplate.queryForObject(
                "SELECT id FROM subjects WHERE tenant_id = ? AND code = ?", Long.class, tenantId, uniqueCode);
        jdbcTemplate.update(
                "INSERT INTO class_subject_hours (tenant_id, class_id, subject_id, periods_per_cycle) VALUES (?, ?, ?, ?)",
                tenantId, classId, subjectId, 1);

        mockMvc.perform(delete(CLASSES_URL + "/" + classId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_whenLessonsExist_returns409() throws Exception {
        long classId = createClass("Year 7A");
        insertMinimalLessonReferencingClass(classId);

        mockMvc.perform(delete(CLASSES_URL + "/" + classId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // ── Authorization ──

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(CLASSES_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody("Year 7A", 7, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long id = createClass("Year 7A");

        mockMvc.perform(put(CLASSES_URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody("Year 7A", 7, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long id = createClass("Year 7A");

        mockMvc.perform(delete(CLASSES_URL + "/" + id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_crossTenant_returns404() throws Exception {
        long id = createClass("Year 7A");

        mockMvc.perform(get(CLASSES_URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ──

    private Map<String, Object> classBody(String name, Integer yearLevel, Long homeroomId, Integer capacity) {
        var body = new java.util.HashMap<String, Object>();
        body.put("name", name);
        if (yearLevel != null) body.put("yearLevel", yearLevel);
        if (homeroomId != null) body.put("homeroomId", homeroomId);
        if (capacity != null) body.put("capacity", capacity);
        return body;
    }

    private long createClass(String name) throws Exception {
        MvcResult r = mockMvc.perform(post(CLASSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(classBody(name, 7, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    /**
     * Inserts a minimal lesson row referencing {@code classId} (FK chain: academic year → term → bell → period → timetable → lesson).
     */
    private void insertMinimalLessonReferencingClass(long classId) {
        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM school_classes WHERE id = ?", Long.class, classId);
        jdbcTemplate.update(
                "INSERT INTO academic_years (tenant_id, name, start_date, end_date, is_active) VALUES (?, ?, ?, ?, ?)",
                tenantId, "AY Lesson Guard", Date.valueOf("2025-01-01"), Date.valueOf("2025-12-31"), true);
        Long ayId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM academic_years WHERE tenant_id = ?", Long.class, tenantId);
        jdbcTemplate.update(
                "INSERT INTO terms (tenant_id, academic_year_id, name, ordinal, start_date, end_date) VALUES (?, ?, ?, ?, ?, ?)",
                tenantId, ayId, "T1", 1, Date.valueOf("2025-01-01"), Date.valueOf("2025-06-30"));
        Long termId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM terms WHERE tenant_id = ?", Long.class, tenantId);
        jdbcTemplate.update(
                "INSERT INTO bell_schedules (tenant_id, name, is_default) VALUES (?, ?, ?)",
                tenantId, "Bell L", false);
        Long bellId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM bell_schedules WHERE tenant_id = ?", Long.class, tenantId);
        jdbcTemplate.update(
                "INSERT INTO schedule_periods (bell_schedule_id, tenant_id, name, start_time, end_time, ordinal) VALUES (?, ?, ?, ?, ?, ?)",
                bellId, tenantId, "P1", Time.valueOf("09:00:00"), Time.valueOf("10:00:00"), 1);
        Long periodId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM schedule_periods WHERE tenant_id = ?", Long.class, tenantId);
        jdbcTemplate.update(
                "INSERT INTO timetables (tenant_id, term_id, bell_schedule_id, name, status) VALUES (?, ?, ?, ?, ?)",
                tenantId, termId, bellId, "TT Lesson Guard", "DRAFT");
        Long ttId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM timetables WHERE tenant_id = ?", Long.class, tenantId);

        String uniqueCode = "LSN" + classId;
        jdbcTemplate.update(
                "INSERT INTO subjects (tenant_id, name, code, color, spread_pattern) VALUES (?, ?, ?, ?, ?)",
                tenantId, "Lesson Subject", uniqueCode, "#001122", "ANY");
        Long subjectId = jdbcTemplate.queryForObject(
                "SELECT id FROM subjects WHERE tenant_id = ? AND code = ?", Long.class, tenantId, uniqueCode);

        Long teacherId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE tenant_id = ? AND role = 'TEACHER' ORDER BY id LIMIT 1",
                Long.class, tenantId);

        jdbcTemplate.update(
                """
                        INSERT INTO lessons (tenant_id, timetable_id, subject_id, class_id, teacher_user_id, schedule_period_id, scheduled_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                tenantId, ttId, subjectId, classId, teacherId, periodId, Date.valueOf("2025-03-15"));
    }

    private long createRoom(String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "type", "CLASSROOM"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createTeacherUser(String email) throws Exception {
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

        return loginAndGetToken(email, PASSWORD);
    }
}
