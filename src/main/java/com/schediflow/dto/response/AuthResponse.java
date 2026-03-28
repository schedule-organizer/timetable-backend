package com.schediflow.dto.response;

/**
 * Response body for successful authentication endpoints.
 * The refresh token is NOT included here — it is set as an HttpOnly cookie by the controller.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AuthResponse of(String accessToken, long expiresInSeconds) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
