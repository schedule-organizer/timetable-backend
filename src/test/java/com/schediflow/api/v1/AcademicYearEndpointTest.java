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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for CRUD /api/v1/academic-years.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AcademicYearEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private static final String URL = "/api/v1/academic-years";
    private static final String PASSWORD = "Password1";

    @BeforeEach
    void setup() throws Exception {
        // Tenant A — admin
        String adminEmail = "admin+" + UUID.randomUUID() + "@acyr-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "AcYr School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@acyr-test.edu";
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

        // Tenant B — separate institution admin (for tenant-isolation test)
        String otherEmail = "admin+" + UUID.randomUUID() + "@other-school.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other School " + UUID.randomUUID(),
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

    private Map<String, Object> yearPayload(String name, String start, String end, boolean isActive) {
        return Map.of("name", name, "startDate", start, "endDate", end, "isActive", isActive);
    }

    private long createYear(String name, String start, String end, boolean isActive) throws Exception {
        MvcResult result = mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(yearPayload(name, start, end, isActive))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Test
    void post_asAdmin_creates201WithBody() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("2026/27", "2026-09-01", "2027-06-30", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("2026/27"))
                .andExpect(jsonPath("$.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.endDate").value("2027-06-30"))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void post_withStartDateEqualEndDate_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("Bad Year", "2026-09-01", "2026-09-01", false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_withStartDateAfterEndDate_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("Bad Year", "2027-06-30", "2026-09-01", false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_withMissingName_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-09-01\",\"endDate\":\"2027-06-30\",\"isActive\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_withNameTooLong_returns400() throws Exception {
        String tooLong = "x".repeat(101);
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload(tooLong, "2026-09-01", "2027-06-30", false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("2026/27", "2026-09-01", "2027-06-30", false))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void post_settingActiveTrue_deactivatesPreviousActiveYear() throws Exception {
        // Create first active year
        long firstId = createYear("2025/26", "2025-09-01", "2026-06-30", true);

        // Create second year as active — should deactivate first
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("2026/27", "2026-09-01", "2027-06-30", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isActive").value(true));

        // First year should now be inactive
        mockMvc.perform(get(URL + "/" + firstId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    // ── GET list ──────────────────────────────────────────────────────────────

    @Test
    void getList_returnsOnlyCurrentTenantYears() throws Exception {
        createYear("2026/27", "2026-09-01", "2027-06-30", false);

        // Tenant A sees its own year
        mockMvc.perform(get(URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        // Tenant B sees no years (tenant isolation)
        mockMvc.perform(get(URL)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getList_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    // ── GET by id ─────────────────────────────────────────────────────────────

    @Test
    void getById_exists_returns200() throws Exception {
        long id = createYear("2026/27", "2026-09-01", "2027-06-30", false);

        mockMvc.perform(get(URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("2026/27"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        mockMvc.perform(get(URL + "/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_crossTenant_returns404() throws Exception {
        long id = createYear("2026/27", "2026-09-01", "2027-06-30", false);

        // Other tenant cannot see tenant A's year (tenant filter → 404)
        mockMvc.perform(get(URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @Test
    void put_updatesYear_returns200() throws Exception {
        long id = createYear("2026/27", "2026-09-01", "2027-06-30", false);

        mockMvc.perform(put(URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("2026/27 Updated", "2026-09-01", "2027-06-30", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2026/27 Updated"));
    }

    @Test
    void put_notFound_returns404() throws Exception {
        mockMvc.perform(put(URL + "/99999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("X", "2026-09-01", "2027-06-30", false))))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_withoutJwt_returns401() throws Exception {
        mockMvc.perform(put(URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("X", "2026-09-01", "2027-06-30", false))))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingYear_returns204() throws Exception {
        long id = createYear("2026/27", "2026-09-01", "2027-06-30", false);

        mockMvc.perform(delete(URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Confirm it's gone
        mockMvc.perform(get(URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete(URL + "/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_withoutJwt_returns401() throws Exception {
        mockMvc.perform(delete(URL + "/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Role guard (TEACHER) ───────────────────────────────────────────────────

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("2026/27", "2026-09-01", "2027-06-30", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long id = createYear("2026/27", "2026-09-01", "2027-06-30", false);

        mockMvc.perform(put(URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                yearPayload("2026/27 Updated", "2026-09-01", "2027-06-30", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long id = createYear("2026/27", "2026-09-01", "2027-06-30", false);

        mockMvc.perform(delete(URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }
}
