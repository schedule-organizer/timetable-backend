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
class SubjectEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @SpyBean EmailService emailService;

    private static final String SUBJECTS_URL = "/api/v1/subjects";
    private static final String CLASSES_URL = "/api/v1/classes";
    private static final String PASSWORD = "Password1";

    private String adminToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@subject-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Subject School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        teacherToken = createTeacherUser("teacher+" + UUID.randomUUID() + "@subject-test.edu");

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-subject.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other Subject School " + UUID.randomUUID(),
                                "email", otherEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);
    }

    @Test
    void post_asAdmin_createsSubject_returns201() throws Exception {
        mockMvc.perform(post(SUBJECTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subjectBody("Mathematics", "MAT", "#AA00FF"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Mathematics"))
                .andExpect(jsonPath("$.code").value("MAT"))
                .andExpect(jsonPath("$.color").value("#AA00FF"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void post_invalidColor_returns400() throws Exception {
        Map<String, Object> body = subjectBody("Math", "MAT", "red");
        mockMvc.perform(post(SUBJECTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_duplicateCode_returns409() throws Exception {
        mockMvc.perform(post(SUBJECTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subjectBody("Math A", "MAT", "#AA00FF"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(SUBJECTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subjectBody("Math B", "mat", "#00FFAA"))))
                .andExpect(status().isConflict());
    }

    @Test
    void put_duplicateCode_returns409() throws Exception {
        createSubject("Physics", "PHY", "#00FFAA");
        long mathId = createSubject("Math", "MAT", "#AA00FF");

        mockMvc.perform(put(SUBJECTS_URL + "/" + mathId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subjectBody("Math", "PHY", "#AA00FF"))))
                .andExpect(status().isConflict());
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(SUBJECTS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subjectBody("Math", "MAT", "#AA00FF"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long id = createSubject("Math", "MAT", "#AA00FF");

        mockMvc.perform(put(SUBJECTS_URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subjectBody("Math", "MAT", "#AA00FF"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long id = createSubject("Math", "MAT", "#AA00FF");

        mockMvc.perform(delete(SUBJECTS_URL + "/" + id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getList_asTeacher_returns200() throws Exception {
        createSubject("Science", "SCI", "#00AAFF");

        mockMvc.perform(get(SUBJECTS_URL).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getById_crossTenant_returns404() throws Exception {
        long id = createSubject("Math", "MAT", "#AA00FF");

        mockMvc.perform(get(SUBJECTS_URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_whenClassSubjectHoursExist_returns409() throws Exception {
        long subjectId = createSubject("Math", "MAT", "#AA00FF");
        long classId = createClass("Guard Class");
        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM subjects WHERE id = ?", Long.class, subjectId);
        jdbcTemplate.update(
                "INSERT INTO class_subject_hours (tenant_id, class_id, subject_id, periods_per_cycle) VALUES (?, ?, ?, ?)",
                tenantId,
                classId,
                subjectId,
                1);

        mockMvc.perform(delete(SUBJECTS_URL + "/" + subjectId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_softDelete_allowsReuseOfCode() throws Exception {
        long id = createSubject("Math", "MAT", "#AA00FF");

        mockMvc.perform(delete(SUBJECTS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(SUBJECTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subjectBody("Mathematics New", "MAT", "#FF00AA"))))
                .andExpect(status().isCreated());
    }

    private Map<String, Object> subjectBody(String name, String code, String color) {
        return Map.of(
                "name", name,
                "code", code,
                "color", color,
                "difficultyLevel", 3,
                "requiredRoomType", "CLASSROOM",
                "maxPerDay", 2,
                "spreadPattern", "SPREAD");
    }

    private long createClass(String name) throws Exception {
        MvcResult r = mockMvc.perform(post(CLASSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "yearLevel", 7))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createSubject(String name, String code, String color) throws Exception {
        MvcResult r = mockMvc.perform(post(SUBJECTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subjectBody(name, code, color))))
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
