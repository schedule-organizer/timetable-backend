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

import java.util.HashMap;
import java.util.List;
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
class OptionBlockEndpointTest {

    private static final String BLOCKS_URL = "/api/v1/option-blocks";
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
    private long classId;
    private long optionGroupA;
    private long optionGroupB;
    private long setGroup;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@ob-test.edu";
        register(adminEmail, "OB School " + UUID.randomUUID());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        modToken = createModUser("mod+" + UUID.randomUUID() + "@ob-test.edu");

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@ob-test.edu";
        long teacherUserId = inviteAndGetUserId(teacherEmail);
        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-ob.edu";
        register(otherEmail, "Other OB " + UUID.randomUUID());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);

        teacherId = createTeacherProfile(teacherUserId, "Ms Option");
        classId = createClass("9A");
        optionGroupA = createGroup("French", "OPTION_BLOCK", createSubject("French", "FRE", "#111111"));
        optionGroupB = createGroup("German", "OPTION_BLOCK", createSubject("German", "GER", "#222222"));
        setGroup = createGroup("Maths 9A", "SET", createSubject("Maths", "MTH", "#333333"));
    }

    @Test
    void post_asAdmin_createsBlock_returns201() throws Exception {
        mockMvc.perform(post(BLOCKS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Block A", "Year 9 languages", List.of(optionGroupA, optionGroupB))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Block A"))
                .andExpect(jsonPath("$.description").value("Year 9 languages"))
                .andExpect(jsonPath("$.memberGroupIds.length()").value(2))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void post_withoutDescription_returns201() throws Exception {
        mockMvc.perform(post(BLOCKS_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Block B", null, List.of(optionGroupA, optionGroupB))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(nullValue()));
    }

    @Test
    void post_withSingleMember_returns400() throws Exception {
        mockMvc.perform(post(BLOCKS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Too small", null, List.of(optionGroupA))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_withNonOptionBlockMember_returns400() throws Exception {
        mockMvc.perform(post(BLOCKS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Mixed types", null, List.of(optionGroupA, setGroup))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_withUnknownMember_returns404() throws Exception {
        mockMvc.perform(post(BLOCKS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ghost", null, List.of(optionGroupA, 999_999_999L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_memberAlreadyInAnotherBlock_returns409() throws Exception {
        long optionGroupC = createGroup("Spanish", "OPTION_BLOCK", createSubject("Spanish", "SPA", "#444444"));
        createBlock("First", List.of(optionGroupA, optionGroupB));

        mockMvc.perform(post(BLOCKS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Second", null, List.of(optionGroupB, optionGroupC))))
                .andExpect(status().isConflict());
    }

    @Test
    void put_replacesMembership() throws Exception {
        long optionGroupC = createGroup("Spanish", "OPTION_BLOCK", createSubject("Spanish", "SPA", "#444444"));
        long blockId = createBlock("Before", List.of(optionGroupA, optionGroupB));

        mockMvc.perform(put(BLOCKS_URL + "/" + blockId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("After", "updated", List.of(optionGroupA, optionGroupC))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.memberGroupIds.length()").value(2));

        mockMvc.perform(get(BLOCKS_URL + "/" + blockId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberGroupIds[0]").value(optionGroupA))
                .andExpect(jsonPath("$.memberGroupIds[1]").value(optionGroupC));
    }

    @Test
    void delete_softDeletesAndReleasesMembers() throws Exception {
        long blockId = createBlock("Doomed", List.of(optionGroupA, optionGroupB));

        mockMvc.perform(delete(BLOCKS_URL + "/" + blockId).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BLOCKS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(BLOCKS_URL + "/" + blockId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM option_block_groups WHERE option_block_id = ?", Integer.class, blockId);
        org.assertj.core.api.Assertions.assertThat(remaining).isZero();

        // released members can join a new block
        mockMvc.perform(post(BLOCKS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Replacement", null, List.of(optionGroupA, optionGroupB))))
                .andExpect(status().isCreated());
    }

    @Test
    void get_crossTenant_returns404() throws Exception {
        long blockId = createBlock("Mine", List.of(optionGroupA, optionGroupB));

        mockMvc.perform(get(BLOCKS_URL + "/" + blockId).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(BLOCKS_URL).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(BLOCKS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Nope", null, List.of(optionGroupA, optionGroupB))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long blockId = createBlock("Safe", List.of(optionGroupA, optionGroupB));

        mockMvc.perform(delete(BLOCKS_URL + "/" + blockId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(BLOCKS_URL)).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String body(String name, String description, List<Long> memberGroupIds) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("description", description);
        payload.put("memberGroupIds", memberGroupIds);
        return objectMapper.writeValueAsString(payload);
    }

    private long createBlock(String name, List<Long> memberGroupIds) throws Exception {
        MvcResult r = mockMvc.perform(post(BLOCKS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, null, memberGroupIds)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createGroup(String name, String type, long subjectId) throws Exception {
        MvcResult r = mockMvc.perform(post(GROUPS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "type", type,
                                "teacherId", teacherId,
                                "subjectId", subjectId,
                                "classIds", List.of(classId)))))
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
                                "yearLevel", 9,
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
