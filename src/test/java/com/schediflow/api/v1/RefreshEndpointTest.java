package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for POST /api/v1/auth/refresh.
 * Registers + logs in to obtain a real refresh cookie, then exercises the refresh endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RefreshEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String PASSWORD = "Password1";
    private String testEmail;
    private String refreshTokenValue;

    @BeforeEach
    void registerAndLogin() throws Exception {
        testEmail = "admin+" + UUID.randomUUID() + "@refresh-test.edu";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Refresh Test School",
                                "email", testEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", testEmail,
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the refresh token value from the Set-Cookie header
        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        refreshTokenValue = setCookie.split(";")[0].split("=", 2)[1];
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void refresh_validCookie_returns200WithNewAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshTokenValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void refresh_canBeCalledMultipleTimes_noRotation() throws Exception {
        // Refresh token is not rotated — same cookie works twice
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshTokenValue)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshTokenValue)))
                .andExpect(status().isOk());
    }

    // ── 401 paths ─────────────────────────────────────────────────────────────

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
