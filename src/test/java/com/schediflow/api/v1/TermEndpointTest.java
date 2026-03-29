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

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
class TermEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private static final String TERMS_URL = "/api/v1/terms";
    private static final String YEARS_URL = "/api/v1/academic-years";
    private static final String PASSWORD = "Password1";

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@term-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Term School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@term-test.edu";
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

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-term.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other Term School " + UUID.randomUUID(),
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

    private long createAcademicYear(String name, String start, String end) throws Exception {
        MvcResult r = mockMvc.perform(post(YEARS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "startDate", start,
                                "endDate", end,
                                "isActive", false))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> termBody(long academicYearId, String name, int ordinal, String start, String end) {
        return Map.of(
                "academicYearId", academicYearId,
                "name", name,
                "ordinal", ordinal,
                "startDate", start,
                "endDate", end);
    }

    @Test
    void post_createsTerm_returns201() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");

        mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.academicYearId").value(ay))
                .andExpect(jsonPath("$.name").value("Fall"))
                .andExpect(jsonPath("$.ordinal").value(1));
    }

    @Test
    void post_whenAcademicYearMissing_returns404() throws Exception {
        mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(99999L, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_whenTermOutsideAcademicYear_returns400() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");

        mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Bad", 1, "2025-01-01", "2025-02-01"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_duplicateOrdinal_returns409() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");

        mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Winter", 1, "2027-01-01", "2027-03-31"))))
                .andExpect(status().isConflict());
    }

    @Test
    void getList_returnsTermsForAcademicYear() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");

        mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 2, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Spring", 1, "2027-01-01", "2027-06-15"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(TERMS_URL + "?academicYearId=" + ay)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ordinal").value(1))
                .andExpect(jsonPath("$[1].ordinal").value(2));
    }

    @Test
    void getList_whenAcademicYearOtherTenant_returns404() throws Exception {
        long otherAy = createYearAsOtherTenant("2026/27", "2026-09-01", "2027-06-30");

        mockMvc.perform(get(TERMS_URL + "?academicYearId=" + otherAy)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private long createYearAsOtherTenant(String name, String start, String end) throws Exception {
        MvcResult r = mockMvc.perform(post(YEARS_URL)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "startDate", start,
                                "endDate", end,
                                "isActive", false))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void getById_returns200() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");
        MvcResult created = mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get(TERMS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getById_crossTenant_returns404() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");
        MvcResult created = mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get(TERMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_updatesTerm() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");
        MvcResult created = mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put(TERMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall Updated", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fall Updated"));
    }

    @Test
    void delete_returns204() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");
        MvcResult created = mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete(TERMS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(TERMS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getList_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get(TERMS_URL + "?academicYearId=1")).andExpect(status().isUnauthorized());
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");

        mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");
        MvcResult created = mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put(TERMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "X", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long ay = createAcademicYear("2026/27", "2026-09-01", "2027-06-30");
        MvcResult created = mockMvc.perform(post(TERMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                termBody(ay, "Fall", 1, "2026-09-01", "2026-12-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete(TERMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }
}
