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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/v1/users/me and PUT /api/v1/users/me.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MeEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String PASSWORD = "Password1";
    private String accessToken;
    private String email;

    @BeforeEach
    void registerAndLogin() throws Exception {
        email = "admin+" + UUID.randomUUID() + "@me-test.edu";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Me Test School " + UUID.randomUUID(),
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("accessToken").asText();
    }

    // helper to avoid import of MockMvcRequestBuilders.post which conflicts with static import
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
    }

    // ── GET /api/v1/users/me ──────────────────────────────────────────────────

    @Test
    void getMe_withValidJwt_returnsProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void getMe_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/v1/users/me ──────────────────────────────────────────────────

    @Test
    void updateMe_displayName_returns200WithUpdatedName() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", "Alice Admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice Admin"));
    }

    @Test
    void updateMe_passwordChange_correctCurrent_returns200() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD,
                                "newPassword", "NewPassword1"))))
                .andExpect(status().isOk());
    }

    @Test
    void updateMe_passwordChange_wrongCurrent_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "WrongPassword1",
                                "newPassword", "NewPassword1"))))
                .andExpect(status().isBadRequest());
    }
}
