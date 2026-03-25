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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for POST /api/v1/auth/complete-registration.
 * Uses @SpyBean on EmailService to capture the raw invite token from the sent URL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CompleteRegistrationEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean EmailService emailService;

    private static final String PASSWORD = "Password1";
    private String adminAccessToken;
    private String teacherEmail;
    private String rawInviteToken;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@cr-test.edu";
        teacherEmail = "teacher+" + UUID.randomUUID() + "@cr-test.edu";

        // Register admin
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "CR Test School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());

        // Login admin
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", adminEmail, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        adminAccessToken = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("accessToken").asText();

        // Invite teacher — capture the raw token from the URL sent to EmailService
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", teacherEmail))))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvitation(any(), urlCaptor.capture());
        String inviteUrl = urlCaptor.getValue();
        rawInviteToken = inviteUrl.substring(inviteUrl.indexOf("token=") + 6);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void completeRegistration_validToken_returns200WithAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawInviteToken,
                                "password", "NewPassword1",
                                "displayName", "Ms Smith"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.expiresIn").exists())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andReturn();

        String accessToken = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("accessToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    void completeRegistration_tokenSingleUse_secondCallReturns400() throws Exception {
        // First call — succeeds
        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawInviteToken,
                                "password", "NewPassword1"))))
                .andExpect(status().isOk());

        // Second call — token already used
        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawInviteToken,
                                "password", "NewPassword1"))))
                .andExpect(status().isBadRequest());
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Test
    void completeRegistration_invalidToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "completely-wrong-token",
                                "password", "NewPassword1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeRegistration_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", "NewPassword1"))))
                .andExpect(status().isBadRequest());
    }
}
