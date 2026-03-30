package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.exception.BadGatewayException;
import com.schediflow.exception.BadRequestException;
import com.schediflow.integration.holiday.HolidayFeedClient;
import com.schediflow.integration.holiday.HolidayFeedItem;
import com.schediflow.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HolidayImportEndpointTest {

    private static final String IMPORT_URL = "/api/v1/holidays/import";
    private static final String CAL_URL = "/api/v1/holiday-calendars";
    private static final String YEARS_URL = "/api/v1/academic-years";
    private static final String PASSWORD = "Password1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @org.springframework.boot.test.mock.mockito.SpyBean EmailService emailService;

    @MockBean HolidayFeedClient holidayFeedClient;

    private String adminToken;
    private String teacherToken;
    private String modToken;
    private long academicYearId;
    private long calendarId;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@hol-import.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Import School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@hol-import.edu";
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

        modToken = createModUser("mod+" + UUID.randomUUID() + "@hol-import.edu");

        academicYearId = createAcademicYear(adminToken, "2025-2026", "2025-09-01", "2026-06-30");
        calendarId = createCalendar(adminToken, "Import Cal", academicYearId);

        when(holidayFeedClient.fetchPublicHolidays(anyString(), anyInt(), nullable(String.class)))
                .thenReturn(List.of(
                        new HolidayFeedItem("New", LocalDate.of(2026, 1, 1)),
                        new HolidayFeedItem("Independence", LocalDate.of(2026, 7, 4))));
    }

    @Test
    void postImport_returns200WithCounts() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 2026))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0));

        verify(holidayFeedClient).fetchPublicHolidays("US", 2026, null);
    }

    @Test
    void postImport_secondRun_skipsAll() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 2026))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 2026))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.skipped").value(2));
    }

    @Test
    void postImport_unknownCalendar_returns404() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", 99999,
                                "country", "US",
                                "year", 2026))))
                .andExpect(status().isNotFound());
    }

    @Test
    void postImport_feedUnreachable_returns502WithMessage() throws Exception {
        when(holidayFeedClient.fetchPublicHolidays(anyString(), anyInt(), nullable(String.class)))
                .thenThrow(new BadGatewayException("The holiday provider is temporarily unavailable. Please try again later."));

        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 2026))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_GATEWAY"))
                .andExpect(jsonPath("$.message").value("The holiday provider is temporarily unavailable. Please try again later."));
    }

    @Test
    void postImport_missingApiKey_returns400() throws Exception {
        when(holidayFeedClient.fetchPublicHolidays(anyString(), anyInt(), nullable(String.class)))
                .thenThrow(new BadRequestException("Calendarific API key is not configured. Set CALENDARIFIC_API_KEY."));

        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 2026))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Calendarific API key is not configured. Set CALENDARIFIC_API_KEY."));
    }

    @Test
    void postImport_withRegion_forwardsToFeedClient() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "GB",
                                "region", "gb-eng",
                                "year", 2026))))
                .andExpect(status().isOk());

        verify(holidayFeedClient).fetchPublicHolidays("GB", 2026, "gb-eng");
    }

    @Test
    void postImport_invalidCountryCode_returns400() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "12",
                                "year", 2026))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void postImport_yearBelowMin_returns400() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 1800))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void postImport_yearAboveMax_returns400() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 2200))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void postImport_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 2026))))
                .andExpect(status().isForbidden());
    }

    @Test
    void postImport_asMod_returns200() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 2026))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));
    }

    @Test
    void postImport_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post(IMPORT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "calendarId", calendarId,
                                "country", "US",
                                "year", 2026))))
                .andExpect(status().isUnauthorized());
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
        MvcResult r = mockMvc.perform(post(CAL_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "academicYearId", yearId))))
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
