package com.schediflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.domain.Tenant;
import com.schediflow.dto.response.PublicSettingsResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSettingsServiceTest {

    @Mock
    TenantRepository tenantRepository;

    @Mock
    CacheManager cacheManager;

    private TenantSettingsService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Long TENANT_ID = 7L;

    @BeforeEach
    void setUp() {
        service = new TenantSettingsService(tenantRepository, objectMapper, cacheManager);
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
    void updateSettings_evictsPublicSettingsCache() throws Exception {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("publicSettings")).thenReturn(cache);
        Tenant tenant = new Tenant();
        tenant.setSlug("my-slug");
        tenant.setSettings("{}");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(TENANT_ID, objectMapper.readTree("{\"locale\":\"de\"}"));

        verify(cache).evict("my-slug");
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
        // Service rejects non-object payloads before ever touching the repository
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

    // ── getPublicSettings ─────────────────────────────────────────────────────

    @Test
    void getPublicSettings_blankSlug_throwsBadRequest() {
        assertThatThrownBy(() -> service.getPublicSettings(""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tenantSlug");
        assertThatThrownBy(() -> service.getPublicSettings("   "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getPublicSettings_validSlug_returnsProjectedFields() {
        Tenant tenant = new Tenant();
        tenant.setName("Greenfield Academy");
        tenant.setStatus("ACTIVE");
        tenant.setSettings("{\"locale\":\"en_GB\",\"timezone\":\"Europe/London\",\"constraintDefaults\":{}}");
        when(tenantRepository.findBySlug("greenfield")).thenReturn(Optional.of(tenant));

        PublicSettingsResponse response = service.getPublicSettings("greenfield");

        assertThat(response.locale()).isEqualTo("en_GB");
        assertThat(response.timezone()).isEqualTo("Europe/London");
        assertThat(response.institutionName()).isEqualTo("Greenfield Academy");
    }

    @Test
    void getPublicSettings_emptySettings_returnsNullLocaleAndTimezone() {
        Tenant tenant = new Tenant();
        tenant.setName("Empty School");
        tenant.setStatus("ACTIVE");
        tenant.setSettings("{}");
        when(tenantRepository.findBySlug("empty")).thenReturn(Optional.of(tenant));

        PublicSettingsResponse response = service.getPublicSettings("empty");

        assertThat(response.locale()).isNull();
        assertThat(response.timezone()).isNull();
        assertThat(response.institutionName()).isEqualTo("Empty School");
    }

    @Test
    void getPublicSettings_unknownSlug_throwsNotFound() {
        when(tenantRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicSettings("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPublicSettings_inactiveTenant_throwsNotFound() {
        Tenant tenant = new Tenant();
        tenant.setName("Closed School");
        tenant.setStatus("INACTIVE");
        tenant.setSettings("{}");
        when(tenantRepository.findBySlug("closed")).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service.getPublicSettings("closed"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSettings_exceedsMaxDepth_rejected() throws Exception {
        // The depth guard only triggers when merging INTO an existing nested object.
        // Pre-load the tenant with 11 levels of nesting so that merging a 12-level patch
        // will recurse past MAX_MERGE_DEPTH (10) and throw.
        StringBuilder initialSettings = new StringBuilder();
        for (int i = 0; i < 11; i++) initialSettings.append("{\"a\":");
        initialSettings.append("{}");
        for (int i = 0; i < 11; i++) initialSettings.append("}");

        Tenant tenant = new Tenant();
        tenant.setSettings(initialSettings.toString());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        StringBuilder patch = new StringBuilder();
        for (int i = 0; i < 12; i++) patch.append("{\"a\":");
        patch.append("\"v\"");
        for (int i = 0; i < 12; i++) patch.append("}");
        JsonNode patchNode = objectMapper.readTree(patch.toString());

        assertThatThrownBy(() -> service.updateSettings(TENANT_ID, patchNode))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("depth");
    }
}
