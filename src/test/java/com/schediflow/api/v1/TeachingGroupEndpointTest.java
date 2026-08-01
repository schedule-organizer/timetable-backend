package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.List;
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
class TeachingGroupEndpointTest {

    private static final String GROUPS_URL = "/api/v1/teaching-groups";
    private static final String TEACHERS_URL = "/api/v1/teachers";
    private static final String SUBJECTS_URL = "/api/v1/subjects";
    private static final String CLASSES_URL = "/api/v1/classes";
    private static final String PASSWORD = "Password1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private long teacherId;
    private long subjectId;
    private long classAId;
    private long classBId;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@tg-test.edu";
        register(adminEmail, "TG School " + UUID.randomUUID());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        modToken = createModUser("mod+" + UUID.randomUUID() + "@tg-test.edu");

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@tg-test.edu";
        long teacherUserId = inviteAndGetUserId(teacherEmail);
        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-tg.edu";
        register(otherEmail, "Other TG " + UUID.randomUUID());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);

        teacherId = createTeacherProfile(teacherUserId, "Ms Group");
        subjectId = createSubject("Maths", "MTH", "#123456");
        classAId = createClass("7A");
        classBId = createClass("7B");
    }

    @Test
    void post_setGroup_asAdmin_returns201() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("7A Maths", "SET", teacherId, subjectId, List.of(classAId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("7A Maths"))
                .andExpect(jsonPath("$.type").value("SET"))
                .andExpect(jsonPath("$.teacherId").value(teacherId))
                .andExpect(jsonPath("$.subjectId").value(subjectId))
                .andExpect(jsonPath("$.classIds.length()").value(1))
                .andExpect(jsonPath("$.classIds[0]").value(classAId))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void post_mixedGroup_withTwoClasses_returns201() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Combined Maths", "MIXED", teacherId, subjectId, List.of(classAId, classBId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MIXED"))
                .andExpect(jsonPath("$.classIds.length()").value(2));
    }

    @Test
    void post_setGroup_withTwoClasses_returns400() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Bad Set", "SET", teacherId, subjectId, List.of(classAId, classBId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_mixedGroup_withOneClass_returns400() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Bad Mixed", "MIXED", teacherId, subjectId, List.of(classAId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_unknownType_returns400() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Odd", "STREAM", teacherId, subjectId, List.of(classAId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_emptyClassIds_returns400() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("No Classes", "SET", teacherId, subjectId, List.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_unknownTeacher_returns404() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ghost", "SET", 999_999_999L, subjectId, List.of(classAId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_unknownSubject_returns404() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ghost", "SET", teacherId, 999_999_999L, List.of(classAId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_unknownClass_returns404() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ghost", "SET", teacherId, subjectId, List.of(999_999_999L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_duplicateTeacherSubjectClass_returns409() throws Exception {
        createGroup("First", "SET", teacherId, subjectId, List.of(classAId));

        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Second", "MIXED", teacherId, subjectId, List.of(classAId, classBId))))
                .andExpect(status().isConflict());
    }

    @Test
    void post_sameTeacherAndSubject_differentClass_isAllowed() throws Exception {
        createGroup("First", "SET", teacherId, subjectId, List.of(classAId));

        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Second", "SET", teacherId, subjectId, List.of(classBId))))
                .andExpect(status().isCreated());
    }

    @Test
    void put_updatesFieldsAndClassMembership() throws Exception {
        long groupId = createGroup("Before", "SET", teacherId, subjectId, List.of(classAId));

        mockMvc.perform(put(GROUPS_URL + "/" + groupId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("After", "MIXED", teacherId, subjectId, List.of(classAId, classBId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.type").value("MIXED"))
                .andExpect(jsonPath("$.classIds.length()").value(2));

        mockMvc.perform(get(GROUPS_URL + "/" + groupId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classIds.length()").value(2));
    }

    @Test
    void put_toDuplicateCombination_returns409() throws Exception {
        createGroup("A group", "SET", teacherId, subjectId, List.of(classAId));
        long second = createGroup("B group", "SET", teacherId, subjectId, List.of(classBId));

        mockMvc.perform(put(GROUPS_URL + "/" + second)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("B group", "SET", teacherId, subjectId, List.of(classAId))))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_softDeletes_andRemovesFromList() throws Exception {
        long groupId = createGroup("Doomed", "SET", teacherId, subjectId, List.of(classAId));

        mockMvc.perform(delete(GROUPS_URL + "/" + groupId).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(GROUPS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(GROUPS_URL + "/" + groupId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teaching_group_classes WHERE teaching_group_id = ?", Integer.class, groupId);
        org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
    }

    @Test
    void delete_thenRecreate_sameCombination_isAllowed() throws Exception {
        long groupId = createGroup("Old", "SET", teacherId, subjectId, List.of(classAId));
        mockMvc.perform(delete(GROUPS_URL + "/" + groupId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("New", "SET", teacherId, subjectId, List.of(classAId))))
                .andExpect(status().isCreated());
    }

    @Test
    void get_crossTenant_returns404() throws Exception {
        long groupId = createGroup("Mine", "SET", teacherId, subjectId, List.of(classAId));

        mockMvc.perform(get(GROUPS_URL + "/" + groupId).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(GROUPS_URL).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Nope", "SET", teacherId, subjectId, List.of(classAId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long groupId = createGroup("Safe", "SET", teacherId, subjectId, List.of(classAId));

        mockMvc.perform(delete(GROUPS_URL + "/" + groupId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(GROUPS_URL)).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String body(String name, String type, long teacher, long subject, List<Long> classIds) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "type", type,
                "teacherId", teacher,
                "subjectId", subject,
                "classIds", classIds));
    }

    private long createGroup(String name, String type, long teacher, long subject, List<Long> classIds)
            throws Exception {
        MvcResult r = mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, type, teacher, subject, classIds)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createTeacherProfile(long userId, String displayName) throws Exception {
        MvcResult r = mockMvc.perform(post(TEACHERS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "displayName", displayName,
                                "maxPeriodsPerDay", 6,
                                "maxConsecutivePeriods", 3,
                                "workloadCap", 24))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createSubject(String name, String code, String color) throws Exception {
        MvcResult r = mockMvc.perform(post(SUBJECTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "code", code,
                                "color", color,
                                "difficultyLevel", 3,
                                "requiredRoomType", "CLASSROOM",
                                "maxPerDay", 2,
                                "spreadPattern", "SPREAD"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createClass(String name) throws Exception {
        MvcResult r = mockMvc.perform(post(CLASSES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "yearLevel", 7,
                                "capacity", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private void register(String email, String institutionName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", institutionName,
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
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
        inviteAndComplete(email);

        MvcResult usersResult = mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode users = objectMapper.readTree(usersResult.getResponse().getContentAsString()).get("content");
        Long userId = null;
        for (JsonNode user : users) {
            if (email.equals(user.get("email").asText())) {
                userId = user.get("id").asLong();
                break;
            }
        }

        mockMvc.perform(put("/api/v1/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "MOD"))))
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
