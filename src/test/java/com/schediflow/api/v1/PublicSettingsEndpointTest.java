package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.domain.Tenant;
import com.schediflow.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
class PublicSettingsEndpointTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TenantRepository tenantRepository;

    /** Derived from institutionName using the same algorithm as AuthService.generateUniqueSlug. */
    private String tenantSlug;
    private String institutionName;
    private String adminToken;
    private static final String PASSWORD = "Password1";
    private static final String PUBLIC_URL = "/api/v1/settings/public";

    @BeforeEach
    void setUp() throws Exception {
        // Build a unique alphanumeric suffix so slug is predictable (no extra hyphens)
        String uniquePart = UUID.randomUUID().toString().replace("-", "");
        institutionName = "PubSettings " + uniquePart;
        // AuthService slugifies: lowercase, replace [^a-z0-9]+ → '-', trim edges
        tenantSlug = "pubsettings-" + uniquePart;

        String adminEmail = "admin+" + UUID.randomUUID() + "@pubsettings.test";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", institutionName,
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void getPublicSettings_noAuth_returns200() throws Exception {
        mockMvc.perform(get(PUBLIC_URL).param("tenantSlug", tenantSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institutionName").value(institutionName));
    }

    @Test
    void getPublicSettings_returnsLocaleAndTimezoneAfterPut() throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"fr_FR\",\"timezone\":\"Europe/Paris\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(PUBLIC_URL).param("tenantSlug", tenantSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("fr_FR"))
                .andExpect(jsonPath("$.timezone").value("Europe/Paris"))
                .andExpect(jsonPath("$.institutionName").value(institutionName));
    }

    @Test
    void getPublicSettings_doesNotExposeNonPublicFields() throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"constraintDefaults\":{\"maxClassesPerDay\":6}}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(PUBLIC_URL).param("tenantSlug", tenantSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.constraintDefaults").doesNotExist())
                .andExpect(jsonPath("$.terminology").doesNotExist());
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Test
    void getPublicSettings_unknownSlug_returns404() throws Exception {
        mockMvc.perform(get(PUBLIC_URL).param("tenantSlug", "no-such-tenant-xyz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicSettings_inactiveTenant_returns404() throws Exception {
        Tenant tenant = tenantRepository.findBySlug(tenantSlug).orElseThrow();
        tenant.setStatus("INACTIVE");
        tenantRepository.save(tenant);

        mockMvc.perform(get(PUBLIC_URL).param("tenantSlug", tenantSlug))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicSettings_missingSlugParam_returns400() throws Exception {
        mockMvc.perform(get(PUBLIC_URL))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicSettings_blankSlugParam_returns400() throws Exception {
        mockMvc.perform(get(PUBLIC_URL).param("tenantSlug", ""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PUBLIC_URL).param("tenantSlug", "   "))
                .andExpect(status().isBadRequest());
    }
}
