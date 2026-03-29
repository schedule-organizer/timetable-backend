package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
class TenantSettingsEndpointTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @SpyBean
    EmailService emailService;

    private String adminToken;
    private String teacherToken;
    private String modToken;
    private static final String PASSWORD = "Password1";
    private static final String SETTINGS_URL = "/api/v1/settings";

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@settings.test";
        registerInstitution(adminEmail);
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        teacherToken = inviteAndRegister("teacher+" + UUID.randomUUID() + "@settings.test");
        modToken = createModUser("mod+" + UUID.randomUUID() + "@settings.test");
    }

    @Test
    void getSettings_asAdmin_returnsSettingsBlob() throws Exception {
        mockMvc.perform(get(SETTINGS_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    void getSettings_asTeacher_returns403() throws Exception {
        mockMvc.perform(get(SETTINGS_URL)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSettings_asMod_returns403() throws Exception {
        mockMvc.perform(get(SETTINGS_URL)
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void putSettings_asAdmin_mergesAndPersistsConfiguration() throws Exception {
        mockMvc.perform(put(SETTINGS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "terminology", Map.of("class", "form group"),
                                "timezone", "Europe/London"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminology.class").value("form group"))
                .andExpect(jsonPath("$.timezone").value("Europe/London"));

        mockMvc.perform(put(SETTINGS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "terminology", Map.of("period", "session"),
                                "timezone", "America/New_York",
                                "locale", "en_CA",
                                "constraintDefaults", Map.of("maxClassesPerDay", 8)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminology.period").value("session"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.constraintDefaults.maxClassesPerDay").value(8));

        mockMvc.perform(get(SETTINGS_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminology.class").value("form group"))
                .andExpect(jsonPath("$.terminology.period").value("session"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.locale").value("en_CA"))
                .andExpect(jsonPath("$.constraintDefaults.maxClassesPerDay").value(8));
    }

    @Test
    void putSettings_deepMerge_doesNotWipeSiblings() throws Exception {
        mockMvc.perform(put(SETTINGS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"terminology\":{\"class\":\"form\",\"period\":\"lesson\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(put(SETTINGS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"terminology\":{\"period\":\"session\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminology.class").value("form"))
                .andExpect(jsonPath("$.terminology.period").value("session"));
    }

    @Test
    void putSettings_withInvalidTimezone_returnsBadRequest() throws Exception {
        mockMvc.perform(put(SETTINGS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Invalid/Zone\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putSettings_teacherRole_forbidden() throws Exception {
        mockMvc.perform(put(SETTINGS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"fr_FR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void putSettings_modRole_forbidden() throws Exception {
        mockMvc.perform(put(SETTINGS_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"fr_FR\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String inviteAndRegister(String email) throws Exception {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());
        verify(emailService).sendInvitation(eq(email), urlCaptor.capture());
        String rawToken = urlCaptor.getValue();
        rawToken = rawToken.substring(rawToken.indexOf("token=") + 6);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String createModUser(String email) throws Exception {
        inviteAndRegister(email);

        MvcResult usersResult = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode users = objectMapper.readTree(usersResult.getResponse().getContentAsString())
                .get("content");
        Long userId = null;
        for (JsonNode user : users) {
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

    private void registerInstitution(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Institution " + UUID.randomUUID(),
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
