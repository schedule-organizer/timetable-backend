package com.schediflow.dto.response;

/**
 * Projection returned by {@code GET /api/v1/settings/public}.
 * Contains only the fields needed by an unauthenticated login page.
 */
public record PublicSettingsResponse(
        String locale,
        String timezone,
        String institutionName
) {}
