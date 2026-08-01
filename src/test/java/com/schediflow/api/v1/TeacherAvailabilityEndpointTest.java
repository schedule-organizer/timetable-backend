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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
class TeacherAvailabilityEndpointTest {

    private static final String TEACHERS_URL = "/api/v1/teachers";
    private static final String SLOTS_URL = "/api/v1/forbidden-slots";
    private static final String PASSWORD = "Password1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTeacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long teacherId;
    private long otherTeacherId;
    private long periodId;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@av-test.edu";
        register(adminEmail, "AV School " + UUID.randomUUID());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);
        tenantId = jdbcTemplate.queryForObject("SELECT tenant_id FROM users WHERE email = ?", Long.class, adminEmail);

        modToken = createModUser("mod+" + UUID.randomUUID() + "@av-test.edu");

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@av-test.edu";
        long teacherUserId = inviteAndGetUserId(teacherEmail);
        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);

        String otherTeacherEmail = "teacher2+" + UUID.randomUUID() + "@av-test.edu";
        long otherTeacherUserId = inviteAndGetUserId(otherTeacherEmail);
        otherTeacherToken = loginAndGetToken(otherTeacherEmail, PASSWORD);

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-av.edu";
        register(otherEmail, "Other AV " + UUID.randomUUID());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);

        teacherId = createTeacherProfile(teacherUserId, "Ms Grid");
        otherTeacherId = createTeacherProfile(otherTeacherUserId, "Mr Grid");
        periodId = firstSeededPeriodId();
    }

    @Test
    void get_asAdmin_returnsFullWeeklyGrid() throws Exception {
        mockMvc.perform(get(availabilityUrl(teacherId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(teacherId))
                .andExpect(jsonPath("$.periodIds.length()").value(8))
                .andExpect(jsonPath("$.days.length()").value(5))
                .andExpect(jsonPath("$.days[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.days[0].slots.length()").value(8))
                .andExpect(jsonPath("$.days[0].slots[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.dateSpecificUnavailability.length()").value(0));
    }

    @Test
    void get_reflectsRecurringForbiddenSlot() throws Exception {
        createRecurringForbiddenSlot(teacherId, 2);

        MvcResult result = mockMvc.perform(
                        get(availabilityUrl(teacherId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(statusAt(body, 2, periodId)).isEqualTo("UNAVAILABLE");
        org.assertj.core.api.Assertions.assertThat(statusAt(body, 1, periodId)).isEqualTo("AVAILABLE");
    }

    @Test
    void get_listsDateSpecificUnavailabilitySeparately() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityType", "TEACHER");
        payload.put("entityId", teacherId);
        payload.put("specificDate", "2026-09-01");
        payload.put("periodId", periodId);
        payload.put("isRecurring", false);
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(availabilityUrl(teacherId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateSpecificUnavailability.length()").value(1))
                .andExpect(jsonPath("$.dateSpecificUnavailability[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$.dateSpecificUnavailability[0].periodId").value(periodId));
    }

    @Test
    void get_reflectsSoftPreferences() throws Exception {
        insertPreference(teacherId, 1, periodId, "PREFERRED_FREE");
        insertPreference(teacherId, 3, periodId, "PREFERRED_TEACHING");

        MvcResult result = mockMvc.perform(
                        get(availabilityUrl(teacherId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(statusAt(body, 1, periodId)).isEqualTo("PREFERRED_FREE");
        org.assertj.core.api.Assertions.assertThat(statusAt(body, 3, periodId)).isEqualTo("PREFERRED_TEACHING");
    }

    @Test
    void get_forbiddenSlotWinsOverPreference() throws Exception {
        insertPreference(teacherId, 4, periodId, "PREFERRED_TEACHING");
        createRecurringForbiddenSlot(teacherId, 4);

        MvcResult result = mockMvc.perform(
                        get(availabilityUrl(teacherId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(statusAt(body, 4, periodId)).isEqualTo("UNAVAILABLE");
    }

    @Test
    void get_asModerator_isAllowed() throws Exception {
        mockMvc.perform(get(availabilityUrl(teacherId)).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isOk());
    }

    @Test
    void get_asTeacherForSelf_isAllowed() throws Exception {
        mockMvc.perform(get(availabilityUrl(teacherId)).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(teacherId));
    }

    @Test
    void get_asTeacherForAnotherTeacher_returns403() throws Exception {
        mockMvc.perform(get(availabilityUrl(otherTeacherId)).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(availabilityUrl(teacherId)).header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unknownTeacher_returns404() throws Exception {
        mockMvc.perform(get(availabilityUrl(999_999_999L)).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_crossTenant_returns404() throws Exception {
        mockMvc.perform(get(availabilityUrl(teacherId)).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(availabilityUrl(teacherId))).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String availabilityUrl(long id) {
        return TEACHERS_URL + "/" + id + "/availability";
    }

    private String statusAt(JsonNode body, int dayOfWeek, long period) {
        for (JsonNode day : body.get("days")) {
            if (day.get("dayOfWeek").asInt() == dayOfWeek) {
                for (JsonNode slot : day.get("slots")) {
                    if (slot.get("periodId").asLong() == period) {
                        return slot.get("status").asText();
                    }
                }
            }
        }
        throw new AssertionError("No cell for day " + dayOfWeek + " period " + period);
    }

    private void createRecurringForbiddenSlot(long entityId, int dayOfWeek) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityType", "TEACHER");
        payload.put("entityId", entityId);
        payload.put("dayOfWeek", dayOfWeek);
        payload.put("periodId", periodId);
        payload.put("isRecurring", true);
        mockMvc.perform(post(SLOTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    /** Preferences have no write endpoint yet (FR34 gap), so seed them directly. */
    private void insertPreference(long teacher, int dayOfWeek, long period, String type) {
        jdbcTemplate.update(
                "INSERT INTO teacher_preferences (tenant_id, teacher_id, day_of_week, schedule_period_id,"
                        + " preference_type) VALUES (?, ?, ?, ?, ?)",
                tenantId, teacher, dayOfWeek, period, type);
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
