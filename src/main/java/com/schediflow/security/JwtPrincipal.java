package com.schediflow.security;

/**
 * Immutable principal stored in the SecurityContext after JWT validation.
 * Carries all claims needed by the service layer without re-parsing the token.
 */
public record JwtPrincipal(Long userId, Long tenantId, String role, String email) {}
