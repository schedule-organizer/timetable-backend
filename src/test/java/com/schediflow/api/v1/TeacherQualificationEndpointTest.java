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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
class TeacherQualificationEndpointTest {

    private static final String TEACHERS_URL = "/api/v1/teachers";
    private static final String SUBJECTS_URL = "/api/v1/subjects";
    private static final String PASSWORD = "Password1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;
    private long teacherUserId;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@qual-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Qual School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        modToken = createModUser("mod+" + UUID.randomUUID() + "@qual-test.edu");

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@qual-test.edu";
        inviteAndComplete(teacherEmail);
        teacherUserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, teacherEmail);
        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-qual.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other Qual " + UUID.randomUUID(),
                                "email", otherEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);
    }

    @Test
    void post_asAdmin_createsQualification_returns201() throws Exception {
        long teacherId = createTeacherProfile(teacherUserId, "Ms Q");
        long subjectId = createSubject("Math", "MAT", "#AA00FF");

        mockMvc.perform(post(qualUrl(teacherId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.teacherId").value(teacherId))
                .andExpect(jsonPath("$.subjectId").value(subjectId))
                .andExpect(jsonPath("$.periodsPerCycle").value(nullValue()));
    }

    @Test
    void post_withPeriodsPerCycle_persists() throws Exception {
        long teacherId = createTeacherProfile(teacherUserId, "Mr P");
        long subjectId = createSubject("Sci", "SCI", "#00FFAA");

        mockMvc.perform(post(qualUrl(teacherId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId, "periodsPerCycle", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodsPerCycle").value(5));
    }

    @Test
    void post_periodsPerCycleZero_returns400() throws Exception {
        long teacherId = createTeacherProfile(teacherUserId, "Min P");
        long subjectId = createSubject("Bio", "BIO", "#00AAFF");

        mockMvc.perform(post(qualUrl(teacherId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId, "periodsPerCycle", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getList_andDelete_roundTrip() throws Exception {
        long teacherId = createTeacherProfile(teacherUserId, "List Del");
        long subjectId = createSubject("Art", "ART", "#FF00AA");

        MvcResult created =
                mockMvc.perform(post(qualUrl(teacherId))
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId))))
                        .andExpect(status().isCreated())
                        .andReturn();
        long qualId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get(qualUrl(teacherId)).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(qualId));

        mockMvc.perform(delete(qualUrl(teacherId) + "/" + qualId).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(qualUrl(teacherId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void post_duplicateSubject_returns409() throws Exception {
        long teacherId = createTeacherProfile(teacherUserId, "Dup");
        long subjectId = createSubject("Hist", "HIS", "#112233");
        var body = objectMapper.writeValueAsString(Map.of("subjectId", subjectId));

        mockMvc.perform(post(qualUrl(teacherId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(qualUrl(teacherId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void post_unknownSubject_returns404() throws Exception {
        long teacherId = createTeacherProfile(teacherUserId, "U Sub");

        mockMvc.perform(post(qualUrl(teacherId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subjectId", 999_999_999L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_unknownTeacher_returns404() throws Exception {
        long subjectId = createSubject("Geo", "GEO", "#445566");

        mockMvc.perform(post(qualUrl(999_999_999L))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_wrongTeacherForQualification_returns404() throws Exception {
        long t1 = createTeacherProfile(teacherUserId, "T1");
        long otherUser = inviteAndGetUserId("u+" + UUID.randomUUID() + "@qual-test.edu");
        long t2 = createTeacherProfile(otherUser, "T2");
        long subjectId = createSubject("Mus", "MUS", "#ABCDEF");

        MvcResult r =
                mockMvc.perform(post(qualUrl(t1))
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId))))
                        .andExpect(status().isCreated())
                        .andReturn();
        long qualId = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete(qualUrl(t2) + "/" + qualId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_crossTenant_returns404() throws Exception {
        long teacherId = createTeacherProfile(teacherUserId, "X");
        long subjectId = createSubject("Eng", "ENG", "#DEAD00");
        mockMvc.perform(post(qualUrl(teacherId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(qualUrl(teacherId)).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        long teacherId = createTeacherProfile(teacherUserId, "No Post");
        long subjectId = createSubject("PE", "PE1", "#00BEE0");

        mockMvc.perform(post(qualUrl(teacherId))
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long teacherId = createTeacherProfile(teacherUserId, "No Del");
        long subjectId = createSubject("CS", "CS1", "#B0B0B0");
        MvcResult r =
                mockMvc.perform(post(qualUrl(teacherId))
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("subjectId", subjectId))))
                        .andExpect(status().isCreated())
                        .andReturn();
        long qualId = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete(qualUrl(teacherId) + "/" + qualId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    private String qualUrl(long teacherId) {
        return TEACHERS_URL + "/" + teacherId + "/qualifications";
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

        MvcResult usersResult = mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
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

    private void inviteAndComplete(String email) throws Exception {
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

    private long inviteAndGetUserId(String email) throws Exception {
        inviteAndComplete(email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}
