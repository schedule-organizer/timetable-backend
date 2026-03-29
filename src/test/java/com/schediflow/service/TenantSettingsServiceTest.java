package com.schediflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.domain.Tenant;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSettingsServiceTest {

    @Mock
    TenantRepository tenantRepository;

    private TenantSettingsService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Long TENANT_ID = 7L;

    @BeforeEach
    void setUp() {
        service = new TenantSettingsService(tenantRepository, objectMapper);
    }

    @Test
    void getSettings_returnsCurrentBlob() {
        Tenant tenant = new Tenant();
        tenant.setSettings("{\"locale\":\"en_GB\"}");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        JsonNode settings = service.getSettings(TENANT_ID);

        assertThat(settings.get("locale").asText()).isEqualTo("en_GB");
    }

    @Test
    void updateSettings_mergesNestedObjects_andRespectsTimezone() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setSettings("""
                {
                  "terminology": {
                    "class": "form"
                  },
                  "timezone": "UTC"
                }
                """);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        JsonNode patch = objectMapper.readTree("""
                {
                  "terminology": {
                    "period": "session"
                  },
                  "timezone": "America/New_York",
                  "constraintDefaults": {
                    "maxClassesPerDay": 5
                  }
                }
                """);

        JsonNode updated = service.updateSettings(TENANT_ID, patch);

        assertThat(updated.get("terminology").get("class").asText()).isEqualTo("form");
        assertThat(updated.get("terminology").get("period").asText()).isEqualTo("session");
        assertThat(updated.get("timezone").asText()).isEqualTo("America/New_York");
        assertThat(updated.get("constraintDefaults").get("maxClassesPerDay").asInt()).isEqualTo(5);
        verify(tenantRepository).save(tenant);
    }

    @Test
    void updateSettings_invalidTimezone_bubblesBadRequest() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setSettings("{}");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        JsonNode patch = objectMapper.readTree("{\"timezone\":\"Moon/Null\"}");

        assertThatThrownBy(() -> service.updateSettings(TENANT_ID, patch))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid timezone");
    }

    @Test
    void updateSettings_nonObjectPayload_rejected() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setSettings("{}");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        JsonNode patch = objectMapper.readTree("[]");

        assertThatThrownBy(() -> service.updateSettings(TENANT_ID, patch))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getSettings_missingTenant_throwsNotFound() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSettings(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSettings_nullValueInPatch_rejected() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setSettings("{}");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        JsonNode patch = objectMapper.readTree("{\"timezone\":null}");

        assertThatThrownBy(() -> service.updateSettings(TENANT_ID, patch))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("null");
    }

    @Test
    void updateSettings_exceedsMaxDepth_rejected() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setSettings("{}");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        // Build a JSON object nested 12 levels deep (exceeds MAX_MERGE_DEPTH of 10)
        StringBuilder nested = new StringBuilder();
        for (int i = 0; i < 12; i++) nested.append("{\"a\":");
        nested.append("\"v\"");
        for (int i = 0; i < 12; i++) nested.append("}");
        JsonNode patch = objectMapper.readTree(nested.toString());

        assertThatThrownBy(() -> service.updateSettings(TENANT_ID, patch))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("depth");
    }
}
