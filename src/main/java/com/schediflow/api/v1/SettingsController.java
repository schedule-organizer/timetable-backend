package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.schediflow.dto.response.PublicSettingsResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.TenantSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes tenant-specific configuration such as locale, timezone, terminology,
 * and constraint defaults stored in the {@code tenants.settings} JSONB blob.
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final TenantSettingsService settingsService;

    public SettingsController(TenantSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * GET /api/v1/settings/public
     *
     * <p>Unauthenticated endpoint that returns the public-facing settings for the tenant
     * identified by the {@code tenantSlug} query parameter. Only locale, timezone, and
     * institutionName are returned. Results are cached for 5 minutes.</p>
     *
     * @return 200 OK with public settings; 404 if the slug is unknown or the tenant is inactive
     */
    @GetMapping("/public")
    public ResponseEntity<PublicSettingsResponse> getPublicSettings(
            @RequestParam String tenantSlug) {
        return ResponseEntity.ok(settingsService.getPublicSettings(tenantSlug));
    }

    /**
     * GET /api/v1/settings
     *
     * <p>Returns the current tenant's settings JSON (locale, timezone, terminology overrides,
     * constraint defaults, etc.). Restricted to ADMIN — institution configuration is an
     * administrative surface per the RBAC matrix.</p>
     *
     * @return 200 OK with the settings blob; 401 if unauthenticated; 403 if caller lacks ADMIN role
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonNode> getSettings(@AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(settingsService.getSettings(principal.tenantId()));
    }

    /**
     * PUT /api/v1/settings
     *
     * <p>Performs a partial update by deeply merging the supplied JSON object
     * into the existing settings. Inline timezone values are validated against
     * the IANA database.</p>
     *
     * @return 200 OK with the updated blob; 400 if the payload is malformed or timezone is invalid;
     *         403 if the caller lacks ADMIN privileges
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonNode> updateSettings(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody JsonNode updates) {

        return ResponseEntity.ok(
                settingsService.updateSettings(principal.tenantId(), updates));
    }
}
