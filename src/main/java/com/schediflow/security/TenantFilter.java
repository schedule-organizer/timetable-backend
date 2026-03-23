package com.schediflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts the tenant_id claim from the Bearer JWT and stores it in TenantContext
 * for the duration of the request. Always clears the context in a finally block
 * to prevent thread pool leakage.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public TenantFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractBearerToken(request);
            if (token != null) {
                try {
                    Claims claims = jwtTokenProvider.validateAndExtractClaims(token);
                    Object raw = claims.get("tenantId");
                    if (raw instanceof Number n) {
                        TenantContext.setTenantId(n.longValue());
                    }
                } catch (JwtException ignored) {
                    // Invalid token — tenant context stays null; protected routes will 401 via entry point
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
