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

import java.util.HashMap;
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
class HolidayDateEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String teacherToken;
    private String modToken;
    private String otherTenantAdminToken;
    private long calendarId;
    private long otherTenantCalendarId;

    private static final String DATES_URL_TEMPLATE = "/api/v1/holidays/%d/dates";
    private static final String PASSWORD = "Password1";

    @BeforeEach
    void setup() throws Exception {
        // ── Tenant A — admin ──────────────────────────────────────────────────────
        String adminEmail = "admin+" + UUID.randomUUID() + "@holdate-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "HolDate School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        // ── Tenant A — teacher ────────────────────────────────────────────────────
        String teacherEmail = "teacher+" + UUID.randomUUID() + "@holdate-test.edu";
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

        // ── Tenant A — mod ────────────────────────────────────────────────────────
        modToken = createModUser("mod+" + UUID.randomUUID() + "@holdate-test.edu");

        // ── Tenant B — admin ──────────────────────────────────────────────────────
        String otherEmail = "admin+" + UUID.randomUUID() + "@other-holdate.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other HolDate School " + UUID.randomUUID(),
                                "email", otherEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);

        // ── Seed calendars ────────────────────────────────────────────────────────
        long yearId = createAcademicYear(adminToken, "2025-2026", "2025-09-01", "2026-06-30");
        long otherYearId = createAcademicYear(otherTenantAdminToken, "2025-2026", "2025-09-01", "2026-06-30");
        calendarId = createCalendar(adminToken, "Tenant A Cal", yearId);
        otherTenantCalendarId = createCalendar(otherTenantAdminToken, "Tenant B Cal", otherYearId);
    }

    // ── POST ─────────────────────────────────────────────────────────────────────

    @Test
    void post_addsDate_returns201() throws Exception {
        mockMvc.perform(post(datesUrl(calendarId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dateBody("2026-01-01", "New Year", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.calendarId").value(calendarId))
                .andExpect(jsonPath("$.date").value("2026-01-01"))
                .andExpect(jsonPath("$.name").value("New Year"))
                .andExpect(jsonPath("$.type").value("PUBLIC_HOLIDAY"))
                .andExpect(jsonPath("$.lessonConflicts").isArray());
    }

    @Test
    void post_asMod_returns201() throws Exception {
        mockMvc.perform(post(datesUrl(calendarId))
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dateBody("2026-02-01", "Day Off", "SCHOOL_BREAK"))))
                .andExpect(status().isCreated());
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(datesUrl(calendarId))
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dateBody("2026-01-01", "New Year", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post(datesUrl(calendarId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dateBody("2026-01-01", "New Year", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void post_calendarNotFound_returns404() throws Exception {
        mockMvc.perform(post(datesUrl(99999L))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dateBody("2026-01-01", "New Year", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_crossTenantCalendar_returns404() throws Exception {
        mockMvc.perform(post(datesUrl(otherTenantCalendarId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dateBody("2026-01-01", "New Year", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_duplicateDate_returns400() throws Exception {
        mockMvc.perform(post(datesUrl(calendarId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dateBody("2026-01-01", "New Year", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(datesUrl(calendarId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dateBody("2026-01-01", "Also New Year", "SCHOOL_BREAK"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_missingFields_returns400() throws Exception {
        mockMvc.perform(post(datesUrl(calendarId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── PUT ───────────────────────────────────────────────────────────────────────

    @Test
    void put_updatesNameAndType() throws Exception {
        long dateId = createDate(adminToken, calendarId, "2026-01-01", "New Year", "PUBLIC_HOLIDAY");

        mockMvc.perform(put(datesUrl(calendarId) + "/" + dateId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Year Updated",
                                "type", "SCHOOL_BREAK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dateId))
                .andExpect(jsonPath("$.name").value("New Year Updated"))
                .andExpect(jsonPath("$.type").value("SCHOOL_BREAK"))
                .andExpect(jsonPath("$.date").value("2026-01-01"));
    }

    @Test
    void put_calendarNotFound_returns404() throws Exception {
        mockMvc.perform(put(datesUrl(99999L) + "/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X", "type", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_dateNotFound_returns404() throws Exception {
        mockMvc.perform(put(datesUrl(calendarId) + "/99999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X", "type", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_crossTenantDate_returns404() throws Exception {
        long dateId = createDate(adminToken, calendarId, "2026-03-01", "Spring Day", "SCHOOL_BREAK");

        mockMvc.perform(put(datesUrl(calendarId) + "/" + dateId)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X", "type", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long dateId = createDate(adminToken, calendarId, "2026-01-01", "New Year", "PUBLIC_HOLIDAY");

        mockMvc.perform(put(datesUrl(calendarId) + "/" + dateId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X", "type", "PUBLIC_HOLIDAY"))))
                .andExpect(status().isForbidden());
    }

    // ── DELETE ────────────────────────────────────────────────────────────────────

    @Test
    void delete_returns204() throws Exception {
        long dateId = createDate(adminToken, calendarId, "2026-01-01", "New Year", "PUBLIC_HOLIDAY");

        mockMvc.perform(delete(datesUrl(calendarId) + "/" + dateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_calendarNotFound_returns404() throws Exception {
        mockMvc.perform(delete(datesUrl(99999L) + "/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_dateNotFound_returns404() throws Exception {
        mockMvc.perform(delete(datesUrl(calendarId) + "/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_crossTenantDate_returns404() throws Exception {
        long dateId = createDate(adminToken, calendarId, "2026-05-01", "Labour Day", "PUBLIC_HOLIDAY");

        mockMvc.perform(delete(datesUrl(calendarId) + "/" + dateId)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long dateId = createDate(adminToken, calendarId, "2026-01-01", "New Year", "PUBLIC_HOLIDAY");

        mockMvc.perform(delete(datesUrl(calendarId) + "/" + dateId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private String datesUrl(long calId) {
        return String.format(DATES_URL_TEMPLATE, calId);
    }

    private Map<String, Object> dateBody(String date, String name, String type) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", date);
        map.put("name", name);
        map.put("type", type);
        return map;
    }

    private long createDate(String token, long calId, String date, String name, String type) throws Exception {
        MvcResult r = mockMvc.perform(post(datesUrl(calId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dateBody(date, name, type))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
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

    private long createAcademicYear(String token, String name, String startDate, String endDate) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "startDate", startDate,
                                "endDate", endDate,
                                "isActive", false))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createCalendar(String token, String name, long yearId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("academicYearId", yearId);
        MvcResult r = mockMvc.perform(post("/api/v1/holiday-calendars")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
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
