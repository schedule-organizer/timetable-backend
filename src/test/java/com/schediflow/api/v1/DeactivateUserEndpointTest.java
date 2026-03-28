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
 * Integration tests for DELETE /api/v1/users/{id}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DeactivateUserEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean EmailService emailService;

    private static final String PASSWORD = "Password1";
    private String adminToken;
    private String teacherToken;
    private String teacherEmail;
    private long teacherId;
    private long adminId;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@deact-test.edu";
        teacherEmail = "teacher+" + UUID.randomUUID() + "@deact-test.edu";

        // Register admin
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Deact Test School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());

        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        // Resolve admin's own ID
        MvcResult meResult = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        adminId = objectMapper.readTree(meResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Invite + complete registration for a teacher
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

        // Resolve teacher ID via list
        MvcResult listResult = mockMvc.perform(get("/api/v1/users?role=TEACHER")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        teacherId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("content").get(0).get("id").asLong();
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

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void deactivate_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + teacherId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivate_deactivatedUserCannotLogin_returns401() throws Exception {
        // Deactivate teacher
        mockMvc.perform(delete("/api/v1/users/" + teacherId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Deactivated teacher can no longer log in
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", teacherEmail, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    // ── Self-deactivation guard ───────────────────────────────────────────────

    @Test
    void deactivate_selfDeactivation_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    // ── Not found ─────────────────────────────────────────────────────────────

    @Test
    void deactivate_nonExistentUser_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/users/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── Auth / role guard ─────────────────────────────────────────────────────

    @Test
    void deactivate_asTeacher_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + adminId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivate_withoutJwt_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + teacherId))
                .andExpect(status().isUnauthorized());
    }
}
