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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
class ClassSubjectHourEndpointTest {

    private static final String CLASSES_URL = "/api/v1/classes";
    private static final String SUBJECTS_URL = "/api/v1/subjects";
    private static final String PASSWORD = "Password1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@csh-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "CSH School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        teacherToken = createTeacherUser("teacher+" + UUID.randomUUID() + "@csh-test.edu");

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-csh.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other CSH " + UUID.randomUUID(),
                                "email", otherEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);
    }

    @Test
    void put_thenGet_returnsAllocations() throws Exception {
        long classId = createClass("7A");
        long s1 = createSubject("Math", "MAT", "#AA00FF");
        long s2 = createSubject("Eng", "ENG", "#00AAFF");

        String body =
                objectMapper.writeValueAsString(
                        Map.of(
                                "items",
                                List.of(
                                        Map.of("subjectId", s1, "periodsPerCycle", 10, "spreadPattern", "SPREAD"),
                                        Map.of("subjectId", s2, "periodsPerCycle", 8, "spreadPattern", "CLUSTER"))));

        mockMvc.perform(put(subjectHoursUrl(classId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subjectId").value(s1))
                .andExpect(jsonPath("$[0].periodsPerCycle").value(10))
                .andExpect(jsonPath("$[0].spreadPattern").value("SPREAD"));

        mockMvc.perform(get(subjectHoursUrl(classId)).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void put_empty_clearsMatrix() throws Exception {
        long classId = createClass("7B");
        long s1 = createSubject("Art", "ART", "#FFAA00");

        mockMvc.perform(put(subjectHoursUrl(classId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "items",
                                                List.of(
                                                        Map.of(
                                                                "subjectId",
                                                                s1,
                                                                "periodsPerCycle",
                                                                2,
                                                                "spreadPattern",
                                                                "ANY"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(put(subjectHoursUrl(classId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(subjectHoursUrl(classId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void put_totalExceedsBellCapacity_returns400() throws Exception {
        long classId = createClass("7C");
        long s1 = createSubject("Big", "BIG", "#111111");
        // Seeded default: 7 teaching slots/day × 5 days = 35
        mockMvc.perform(put(subjectHoursUrl(classId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "items",
                                                List.of(
                                                        Map.of(
                                                                "subjectId",
                                                                s1,
                                                                "periodsPerCycle",
                                                                36,
                                                                "spreadPattern",
                                                                "ANY"))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_unknownClass_returns404() throws Exception {
        mockMvc.perform(get(subjectHoursUrl(999_999_999L)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_unknownSubject_returns404() throws Exception {
        long classId = createClass("7D");
        mockMvc.perform(put(subjectHoursUrl(classId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "items",
                                                List.of(
                                                        Map.of(
                                                                "subjectId",
                                                                999_999_999L,
                                                                "periodsPerCycle",
                                                                1,
                                                                "spreadPattern",
                                                                "ANY"))))))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_duplicateSubject_returns400() throws Exception {
        long classId = createClass("7E");
        long s1 = createSubject("Dup", "DUP", "#222222");
        mockMvc.perform(put(subjectHoursUrl(classId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "items",
                                                List.of(
                                                        Map.of("subjectId", s1, "periodsPerCycle", 1, "spreadPattern", "ANY"),
                                                        Map.of("subjectId", s1, "periodsPerCycle", 2, "spreadPattern", "SPREAD"))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long classId = createClass("7F");
        long s1 = createSubject("PE", "PE1", "#333333");
        mockMvc.perform(put(subjectHoursUrl(classId))
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "items",
                                                List.of(
                                                        Map.of(
                                                                "subjectId",
                                                                s1,
                                                                "periodsPerCycle",
                                                                1,
                                                                "spreadPattern",
                                                                "ANY"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_crossTenant_returns404() throws Exception {
        long classId = createClass("7G");
        mockMvc.perform(get(subjectHoursUrl(classId)).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_periodsNotPositive_returns400() throws Exception {
        long classId = createClass("7H");
        long s1 = createSubject("X", "X1", "#444444");
        mockMvc.perform(put(subjectHoursUrl(classId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "items",
                                                List.of(
                                                        Map.of(
                                                                "subjectId",
                                                                s1,
                                                                "periodsPerCycle",
                                                                0,
                                                                "spreadPattern",
                                                                "ANY"))))))
                .andExpect(status().isBadRequest());
    }

    private String subjectHoursUrl(long classId) {
        return CLASSES_URL + "/" + classId + "/subject-hours";
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
        MvcResult r =
                mockMvc.perform(post(SUBJECTS_URL)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(subjectBody(name, code, color))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createClass(String name) throws Exception {
        MvcResult r =
                mockMvc.perform(post(CLASSES_URL)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("name", name, "yearLevel", 7))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result =
                mockMvc.perform(post("/api/v1/auth/login")
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
