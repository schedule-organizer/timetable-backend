package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full Spring Boot integration tests for POST /api/v1/auth/login.
 * Uses H2 in-memory DB. Each test uses a UUID-based email to avoid conflicts
 * across test runs (H2 DB persists across @DirtiesContext resets due to DB_CLOSE_DELAY=-1).
 *
 * max-requests=4 so @BeforeEach register (1) + 3 ok logins + 1 rejected = 429.
 * @DirtiesContext resets the rate limiter's in-memory state between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.ratelimit.max-requests=4")
class LoginEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String VALID_PASSWORD = "Password1";
    private String testEmail;

    @BeforeEach
    void registerUser() throws Exception {
        testEmail = "admin+" + UUID.randomUUID() + "@testschool.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Test School",
                                "email", testEmail,
                                "password", VALID_PASSWORD))))
                .andExpect(status().isCreated());
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(testEmail, VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void login_validCredentials_setsHttpOnlyRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(testEmail, VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth/refresh")));
    }

    // ── 401 paths ─────────────────────────────────────────────────────────────

    @Test
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nobody@nowhere.edu", VALID_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(testEmail, "WrongPass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void login_wrongCredentials_responseGivesNoHintWhichFieldFailed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(testEmail, "BadPass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    @Test
    void login_rateLimitExceeded_returns429() throws Exception {
        // max-requests=4; @BeforeEach register already used 1 slot
        // 3 logins succeed (slots 2, 3, 4), 4th login → slot 5 > 4 → 429
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(testEmail, VALID_PASSWORD)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(testEmail, VALID_PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void login_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "pass1234"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("not-an-email", "pass1234")))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String credentials(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }
}
