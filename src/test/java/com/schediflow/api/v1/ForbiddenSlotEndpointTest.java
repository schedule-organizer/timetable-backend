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
class ForbiddenSlotEndpointTest {

    private static final String SLOTS_URL = "/api/v1/forbidden-slots";
    private static final String TEACHERS_URL = "/api/v1/teachers";
    private static final String ROOMS_URL = "/api/v1/rooms";
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
    private long otherTeacherId;
    private long roomId;
    private long classId;
    private long periodId;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@fs-test.edu";
        register(adminEmail, "FS School " + UUID.randomUUID());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        modToken = createModUser("mod+" + UUID.randomUUID() + "@fs-test.edu");

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@fs-test.edu";
        long teacherUserId = inviteAndGetUserId(teacherEmail);
        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);

        long otherTeacherUserId = inviteAndGetUserId("teacher2+" + UUID.randomUUID() + "@fs-test.edu");

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-fs.edu";
        register(otherEmail, "Other FS " + UUID.randomUUID());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);

        teacherId = createTeacherProfile(teacherUserId, "Ms Busy");
        otherTeacherId = createTeacherProfile(otherTeacherUserId, "Mr Other");
        roomId = createRoom("Lab 1");
        classId = createClass("8A");
        periodId = firstSeededPeriodId();
    }

    @Test
    void post_recurringSlot_asAdmin_returns201() throws Exception {
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("TEACHER", teacherId, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.entityType").value("TEACHER"))
                .andExpect(jsonPath("$.entityId").value(teacherId))
                .andExpect(jsonPath("$.dayOfWeek").value(3))
                .andExpect(jsonPath("$.specificDate").value(nullValue()))
                .andExpect(jsonPath("$.periodId").value(periodId))
                .andExpect(jsonPath("$.isRecurring").value(true));
    }

    @Test
    void post_oneOffSlot_forRoom_asMod_returns201() throws Exception {
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oneOffBody("ROOM", roomId, "2026-09-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityType").value("ROOM"))
                .andExpect(jsonPath("$.dayOfWeek").value(nullValue()))
                .andExpect(jsonPath("$.specificDate").value("2026-09-01"))
                .andExpect(jsonPath("$.isRecurring").value(false));
    }

    @Test
    void post_forClass_returns201() throws Exception {
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("CLASS", classId, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entityType").value("CLASS"));
    }

    @Test
    void post_recurringWithoutDayOfWeek_returns400() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityType", "TEACHER");
        payload.put("entityId", teacherId);
        payload.put("periodId", periodId);
        payload.put("isRecurring", true);

        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_oneOffWithoutSpecificDate_returns400() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityType", "TEACHER");
        payload.put("entityId", teacherId);
        payload.put("periodId", periodId);
        payload.put("isRecurring", false);

        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_dayOfWeekOutOfRange_returns400() throws Exception {
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("TEACHER", teacherId, 8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_unknownEntityType_returns400() throws Exception {
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("BUILDING", roomId, 2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_unknownEntityId_returns404() throws Exception {
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("TEACHER", 999_999_999L, 2)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_unknownPeriodId_returns404() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityType", "TEACHER");
        payload.put("entityId", teacherId);
        payload.put("dayOfWeek", 2);
        payload.put("periodId", 999_999_999L);
        payload.put("isRecurring", true);

        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_crossTenantEntity_returns404() throws Exception {
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("TEACHER", teacherId, 2)))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_duplicateSlot_returns409() throws Exception {
        String body = recurringBody("TEACHER", teacherId, 4);
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void get_listsOnlyThatEntitysSlots() throws Exception {
        createSlot(recurringBody("TEACHER", teacherId, 1));
        createSlot(recurringBody("TEACHER", teacherId, 2));
        createSlot(recurringBody("ROOM", roomId, 1));

        mockMvc.perform(get(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("entityType", "TEACHER")
                        .param("entityId", String.valueOf(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("entityType", "ROOM")
                        .param("entityId", String.valueOf(roomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void get_missingParams_returns400() throws Exception {
        mockMvc.perform(get(SLOTS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_removesSlot() throws Exception {
        long slotId = createSlot(recurringBody("TEACHER", teacherId, 5));

        mockMvc.perform(delete(SLOTS_URL + "/" + slotId).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("entityType", "TEACHER")
                        .param("entityId", String.valueOf(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void delete_unknownSlot_returns404() throws Exception {
        mockMvc.perform(delete(SLOTS_URL + "/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_crossTenant_returns404() throws Exception {
        long slotId = createSlot(recurringBody("TEACHER", teacherId, 5));

        mockMvc.perform(delete(SLOTS_URL + "/" + slotId).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacher_canManageOwnSlots() throws Exception {
        MvcResult created = mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("TEACHER", teacherId, 2)))
                .andExpect(status().isCreated())
                .andReturn();
        long slotId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get(SLOTS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("entityType", "TEACHER")
                        .param("entityId", String.valueOf(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete(SLOTS_URL + "/" + slotId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void teacher_cannotManageAnotherTeachersSlots() throws Exception {
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("TEACHER", otherTeacherId, 2)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(SLOTS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("entityType", "TEACHER")
                        .param("entityId", String.valueOf(otherTeacherId)))
                .andExpect(status().isForbidden());

        long slotId = createSlot(recurringBody("TEACHER", otherTeacherId, 3));
        mockMvc.perform(delete(SLOTS_URL + "/" + slotId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacher_cannotManageRoomOrClassSlots() throws Exception {
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("ROOM", roomId, 2)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurringBody("CLASS", classId, 2)))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(SLOTS_URL).param("entityType", "TEACHER").param("entityId", "1"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String recurringBody(String entityType, long entityId, int dayOfWeek) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityType", entityType);
        payload.put("entityId", entityId);
        payload.put("dayOfWeek", dayOfWeek);
        payload.put("periodId", periodId);
        payload.put("isRecurring", true);
        return objectMapper.writeValueAsString(payload);
    }

    private String oneOffBody(String entityType, long entityId, String isoDate) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityType", entityType);
        payload.put("entityId", entityId);
        payload.put("specificDate", isoDate);
        payload.put("periodId", periodId);
        payload.put("isRecurring", false);
        return objectMapper.writeValueAsString(payload);
    }

    private long createSlot(String body) throws Exception {
        MvcResult r = mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long firstSeededPeriodId() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/bell-schedules").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode schedules = objectMapper.readTree(r.getResponse().getContentAsString());
        return schedules.get(0).get("periods").get(0).get("id").asLong();
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

    private long createRoom(String name) throws Exception {
        MvcResult r = mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "type", "LAB",
                                "capacity", 24))))
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
                                "yearLevel", 8,
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
