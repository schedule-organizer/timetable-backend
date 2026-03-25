package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/v1/users/invite.
 * Uses a registered Admin user to obtain a JWT access token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InviteEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String PASSWORD = "Password1";
    private String adminEmail;
    private String adminAccessToken;
    private String teacherEmail;

    @BeforeEach
    void registerAdminAndLogin() throws Exception {
        adminEmail = "admin+" + UUID.randomUUID() + "@invite-test.edu";
        teacherEmail = "teacher+" + UUID.randomUUID() + "@invite-test.edu";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Invite Test School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", adminEmail, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        adminAccessToken = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("accessToken").asText();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void invite_withAdminJwt_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", teacherEmail))))
                .andExpect(status().isCreated());
    }

    @Test
    void invite_resend_toPendingUser_returns201() throws Exception {
        // First invite — creates PENDING_REGISTRATION user
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", teacherEmail))))
                .andExpect(status().isCreated());

        // Re-send — same email still pending → invalidates old token, issues new one
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", teacherEmail))))
                .andExpect(status().isCreated());
    }

    // ── Conflict ──────────────────────────────────────────────────────────────

    @Test
    void invite_activeUserInTenant_returns409() throws Exception {
        // adminEmail belongs to this tenant and is ACTIVE — inviting it must return 409
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", adminEmail))))
                .andExpect(status().isConflict());
    }

    // ── Auth guard ────────────────────────────────────────────────────────────

    @Test
    void invite_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", teacherEmail))))
                .andExpect(status().isUnauthorized());
    }
}
