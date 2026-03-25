package com.schediflow.api.v1;

import com.schediflow.dto.request.RegisterRequest;
import com.schediflow.dto.response.AuthResponse;
import com.schediflow.service.AuthService;
import com.schediflow.service.AuthService.RegistrationResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Handles public authentication endpoints.
 * All paths are permitted without authentication in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/register
     *
     * <p>Creates a new institution (Tenant) and its first Admin user in a single
     * transaction. Returns a JWT access token in the body and sets an HttpOnly
     * refresh token cookie.</p>
     *
     * @return 201 Created with {@link AuthResponse}; 409 if email already exists;
     *         400 if validation fails
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        RegistrationResult result = authService.register(
                request.institutionName(), request.email(), request.password());

        addRefreshCookie(response, result.refreshToken(), result.refreshTokenExpiryMs());

        long expiresInSeconds = result.accessTokenExpiryMs() / 1000;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponse.of(result.accessToken(), expiresInSeconds));
    }

    private void addRefreshCookie(HttpServletResponse response, String token, long expiryMs) {
        Cookie cookie = new Cookie("refresh_token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/v1/auth/refresh");
        cookie.setMaxAge((int) (expiryMs / 1000));
        // Secure flag enforced at NGINX level in prod; omitted here to work on plain HTTP locally
        response.addCookie(cookie);
    }
}
