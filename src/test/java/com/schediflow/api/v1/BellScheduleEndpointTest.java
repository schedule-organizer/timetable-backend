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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BellScheduleEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String teacherToken;
    private String modToken;
    private String otherTenantAdminToken;

    private static final String BASE_URL = "/api/v1/bell-schedules";
    private static final String PASSWORD = "Password1";

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@bell-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Bell School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@bell-test.edu";
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", teacherEmail))))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvitation(any(), urlCaptor.capture());
        String inviteUrl = urlCaptor.getValue();
        String rawToken = inviteUrl.substring(inviteUrl.indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "password", PASSWORD))))
                .andExpect(status().isOk());

        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);

        String modEmail = "mod+" + UUID.randomUUID() + "@bell-test.edu";
        modToken = createModUser(modEmail);

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-bell.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other Bell School " + UUID.randomUUID(),
                                "email", otherEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private Map<String, Object> scheduleBody(String name, boolean isDefault, List<Map<String, Object>> periods) {
        return Map.of("name", name, "isDefault", isDefault, "periods", periods);
    }

    private Map<String, Object> periodBody(String name, String start, String end, boolean isBreak, boolean isLunch, int ordinal) {
        return Map.of(
                "name", name,
                "startTime", start,
                "endTime", end,
                "isBreak", isBreak,
                "isLunch", isLunch,
                "ordinal", ordinal);
    }

    private long createSchedule(String name, boolean isDefault, List<Map<String, Object>> periods) throws Exception {
        MvcResult r = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody(name, isDefault, periods))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void post_creates_returns201() throws Exception {
        List<Map<String, Object>> periods = List.of(
                periodBody("Period 1", "08:00", "09:00", false, false, 1),
                periodBody("Lunch", "12:00", "13:00", false, true, 2));

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("Standard", false, periods))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Standard"))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.periods.length()").value(2))
                .andExpect(jsonPath("$.periods[0].name").value("Period 1"))
                .andExpect(jsonPath("$.periods[1].isLunch").value(true));
    }

    @Test
    void post_withOverlappingPeriods_returns400() throws Exception {
        List<Map<String, Object>> periods = List.of(
                periodBody("P1", "08:00", "09:00", false, false, 1),
                periodBody("P2", "08:30", "09:30", false, false, 2));

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("Bad", false, periods))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_isDefaultTrue_deactivatesPreviousDefault() throws Exception {
        long firstId = createSchedule("First", true, List.of());

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("Second", true, List.of()))))
                .andExpect(status().isCreated());

        // First schedule should now be non-default
        mockMvc.perform(get(BASE_URL + "/" + firstId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void getList_returns200() throws Exception {
        createSchedule("S1", false, List.of());
        createSchedule("S2", false, List.of());

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getList_isolatesByTenant() throws Exception {
        createSchedule("S1", false, List.of());

        // Other tenant creates their own schedule
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("Other", false, List.of()))))
                .andExpect(status().isCreated());

        // Admin tenant: seeded default + S1 (not Other tenant's schedule)
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getById_returns200() throws Exception {
        long id = createSchedule("S1", false, List.of(
                periodBody("P1", "08:00", "09:00", false, false, 1)));

        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.periods.length()").value(1));
    }

    @Test
    void getById_crossTenant_returns404() throws Exception {
        long id = createSchedule("S1", false, List.of());

        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_updatesScheduleAndPeriods() throws Exception {
        long id = createSchedule("Original", false, List.of(
                periodBody("Old", "08:00", "09:00", false, false, 1)));

        List<Map<String, Object>> newPeriods = List.of(
                periodBody("New Period", "10:00", "11:00", false, false, 1),
                periodBody("Break", "11:00", "11:15", true, false, 2));

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("Updated", false, newPeriods))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.periods.length()").value(2))
                .andExpect(jsonPath("$.periods[0].name").value("New Period"))
                .andExpect(jsonPath("$.periods[1].isBreak").value(true));
    }

    @Test
    void delete_returns204() throws Exception {
        long id = createSchedule("ToDelete", false, List.of());

        mockMvc.perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_onlyDefaultSchedule_returns400() throws Exception {
        long id = createSchedule("Default", true, List.of());

        mockMvc.perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_defaultWhenAnotherDefaultExists_returns204() throws Exception {
        long id1 = createSchedule("Default1", true, List.of());
        createSchedule("Default2", true, List.of());

        // id1 is now non-default (replaced by Default2), so it can be deleted freely
        mockMvc.perform(delete(BASE_URL + "/" + id1).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void post_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("X", false, List.of()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("X", false, List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long id = createSchedule("S1", false, List.of());

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("X", false, List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long id = createSchedule("S1", false, List.of());

        mockMvc.perform(delete(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    // ── Role guard (MOD) ──────────────────────────────────────────────────────

    @Test
    void post_asMod_returns403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("X", false, List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_asMod_returns403() throws Exception {
        long id = createSchedule("S1", false, List.of());

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scheduleBody("X", false, List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asMod_returns403() throws Exception {
        long id = createSchedule("S1", false, List.of());

        mockMvc.perform(delete(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "password", PASSWORD))))
                .andExpect(status().isOk());

        MvcResult usersResult = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode users = objectMapper
                .readTree(usersResult.getResponse().getContentAsString()).get("content");
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
                        .content(objectMapper.writeValueAsString(Map.of("role", "MOD"))))
                .andExpect(status().isOk());

        return loginAndGetToken(email, PASSWORD);
    }
}
