package com.schediflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schediflow.domain.Tenant;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.exception.SchediFlowException;
import com.schediflow.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;

/**
 * Handles GET/PUT /api/v1/settings for the current tenant.
 * Reads and merges the JSONB blob stored on the {@code tenants.settings} column.
 */
@Service
public class TenantSettingsService {

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public TenantSettingsService(TenantRepository tenantRepository, ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
    }

    public JsonNode getSettings(Long tenantId) {
        Tenant tenant = loadTenant(tenantId);
        return parseSettings(tenant.getSettings()).deepCopy();
    }

    @Transactional
    public JsonNode updateSettings(Long tenantId, JsonNode updates) {
        if (updates == null || !updates.isObject()) {
            throw new BadRequestException("Settings payload must be a JSON object");
        }

        Tenant tenant = loadTenant(tenantId);
        ObjectNode current = parseSettings(tenant.getSettings());
        merge(current, updates);
        validateTimezone(current);
        tenant.setSettings(current.toString());
        tenantRepository.save(tenant);
        return current.deepCopy();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Tenant loadTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
    }

    private static final int MAX_MERGE_DEPTH = 10;

    private ObjectNode parseSettings(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isObject()) {
                return objectMapper.createObjectNode().setAll((ObjectNode) node);
            }
            throw new SchediFlowException("INTERNAL_ERROR", "Tenant settings is corrupted: expected a JSON object", 500);
        } catch (JsonProcessingException e) {
            throw new SchediFlowException("INTERNAL_ERROR", "Failed to parse tenant settings", 500);
        }
    }

    private void merge(ObjectNode target, JsonNode updates) {
        merge(target, updates, 0);
    }

    private void merge(ObjectNode target, JsonNode updates, int depth) {
        if (depth > MAX_MERGE_DEPTH) {
            throw new BadRequestException(
                    "Settings patch nesting exceeds the maximum allowed depth of " + MAX_MERGE_DEPTH);
        }
        Iterator<Map.Entry<String, JsonNode>> fields = updates.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                throw new BadRequestException(
                        "Settings patch must not contain null values; found null for key: '" + key + "'");
            }
            JsonNode existing = target.get(key);
            if (value.isObject() && existing instanceof ObjectNode objectNode) {
                merge(objectNode, value, depth + 1);
            } else {
                target.set(key, value);
            }
        }
    }

    private void validateTimezone(ObjectNode settings) {
        JsonNode timezone = settings.get("timezone");
        if (timezone == null || timezone.isNull()) {
            return;
        }
        if (!timezone.isTextual()) {
            throw new BadRequestException("timezone must be a string");
        }
        try {
            ZoneId.of(timezone.asText());
        } catch (DateTimeException ex) {
            throw new BadRequestException("Invalid timezone: " + timezone.asText());
        }
    }
}
