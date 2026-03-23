package com.schediflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Generates and validates JWT access tokens.
 * Secret is loaded from the JWT_SECRET environment variable (app.jwt.secret property).
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiryMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms}") long accessTokenExpiryMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    /**
     * Generates a signed JWT access token.
     * Payload: { sub: userId, tenantId, role, email }
     */
    public String generateToken(Long userId, Long tenantId, String role, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenantId", tenantId)
                .claim("role", role)
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiryMs))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates the token, returning its claims.
     * Throws {@link JwtException} if the token is invalid or expired.
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
