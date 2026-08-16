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
class HolidayCalendarEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String teacherToken;
    private String modToken;
    private String otherTenantAdminToken;
    private long academicYearId;
    private long otherTenantYearId;

    private static final String BASE_URL = "/api/v1/holiday-calendars";
    private static final String YEARS_URL = "/api/v1/academic-years";
    private static final String PASSWORD = "Password1";

    @BeforeEach
    void setup() throws Exception {
        // ── Tenant A — admin ──────────────────────────────────────────────────────
        String adminEmail = "admin+" + UUID.randomUUID() + "@hol-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Hol School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        // ── Tenant A — teacher ────────────────────────────────────────────────────
        String teacherEmail = "teacher+" + UUID.randomUUID() + "@hol-test.edu";
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
        String modEmail = "mod+" + UUID.randomUUID() + "@hol-test.edu";
        modToken = createModUser(modEmail);

        // ── Tenant B — admin ──────────────────────────────────────────────────────
        String otherEmail = "admin+" + UUID.randomUUID() + "@other-hol.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other Hol School " + UUID.randomUUID(),
                                "email", otherEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);

        // ── Seed academic years ───────────────────────────────────────────────────
        academicYearId = createAcademicYear(adminToken, "2025-2026", "2025-09-01", "2026-06-30");
        otherTenantYearId = createAcademicYear(otherTenantAdminToken, "2025-2026", "2025-09-01", "2026-06-30");
    }

    // ── POST ─────────────────────────────────────────────────────────────────────

    @Test
    void post_creates_returns201() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Summer 2026", academicYearId, "US", "CA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.academicYearId").value(academicYearId))
                .andExpect(jsonPath("$.name").value("Summer 2026"))
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.region").value("CA"));
    }

    @Test
    void post_withNullCountryRegion_returns201() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "No Region",
                                "academicYearId", academicYearId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("No Region"));
    }

    @Test
    void post_unknownAcademicYear_returns404() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Cal", 99999L, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_duplicateCalendarForYear_returns409() throws Exception {
        createCalendar(adminToken, "First", academicYearId);

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Second", academicYearId, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void post_crossTenantAcademicYear_returns404() throws Exception {
        // Tenant A admin tries to use Tenant B's academic year id
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Cal", otherTenantYearId, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_asMod_returns201() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Mod Cal", academicYearId, null, null))))
                .andExpect(status().isCreated());
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Cal", academicYearId, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Cal", academicYearId, null, null))))
                .andExpect(status().isUnauthorized());
    }

    // ── GET list ──────────────────────────────────────────────────────────────────

    @Test
    void getList_returns200() throws Exception {
        createCalendar(adminToken, "Cal A", academicYearId);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getList_isolatesByTenant() throws Exception {
        createCalendar(adminToken, "Tenant A Cal", academicYearId);
        createCalendar(otherTenantAdminToken, "Tenant B Cal", otherTenantYearId);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Tenant A Cal"));
    }

    @Test
    void getList_asTeacher_returns200() throws Exception {
        createCalendar(adminToken, "Cal", academicYearId);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET by id ─────────────────────────────────────────────────────────────────

    @Test
    void getById_returns200() throws Exception {
        long id = createCalendar(adminToken, "Summer", academicYearId);

        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Summer"));
    }

    @Test
    void getById_crossTenant_returns404() throws Exception {
        long id = createCalendar(adminToken, "Summer", academicYearId);

        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/99999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── PUT ───────────────────────────────────────────────────────────────────────

    @Test
    void put_updatesCalendar() throws Exception {
        long id = createCalendar(adminToken, "Original", academicYearId);
        long year2Id = createAcademicYear(adminToken, "2026-2027", "2026-09-01", "2027-06-30");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Updated", year2Id, "GB", "London"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.academicYearId").value(year2Id))
                .andExpect(jsonPath("$.country").value("GB"));
    }

    @Test
    void put_sameAcademicYear_returns200() throws Exception {
        long id = createCalendar(adminToken, "Cal", academicYearId);

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Cal Updated", academicYearId, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cal Updated"));
    }

    @Test
    void put_toAcademicYearAlreadyUsedByAnotherCalendar_returns409() throws Exception {
        long year2Id = createAcademicYear(adminToken, "2026-2027", "2026-09-01", "2027-06-30");
        long calendarOnYear1 = createCalendar(adminToken, "On Year 1", academicYearId);
        createCalendar(adminToken, "On Year 2", year2Id);

        mockMvc.perform(put(BASE_URL + "/" + calendarOnYear1)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Conflict", year2Id, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long id = createCalendar(adminToken, "Cal", academicYearId);

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Cal", academicYearId, null, null))))
                .andExpect(status().isForbidden());
    }

    // ── DELETE ────────────────────────────────────────────────────────────────────

    @Test
    void delete_returns204() throws Exception {
        long id = createCalendar(adminToken, "ToDelete", academicYearId);

        mockMvc.perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long id = createCalendar(adminToken, "Cal", academicYearId);

        mockMvc.perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/99999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

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
        MvcResult r = mockMvc.perform(post(YEARS_URL)
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
        MvcResult r = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody(name, yearId, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> calendarBody(String name, Long yearId, String country, String region) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("name", name);
        map.put("academicYearId", yearId);
        if (country != null) map.put("country", country);
        if (region != null) map.put("region", region);
        return map;
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
                        .content(objectMapper.writeValueAsString(Map.of("role", "MODERATOR"))))
                .andExpect(status().isOk());

        return loginAndGetToken(email, PASSWORD);
    }
}
