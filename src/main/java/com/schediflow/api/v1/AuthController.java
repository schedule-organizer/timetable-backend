package com.schediflow.api.v1;

import com.schediflow.exception.UnauthorizedException;
import com.schediflow.dto.request.CompleteRegistrationRequest;
import com.schediflow.dto.request.LoginRequest;
import com.schediflow.dto.request.RegisterRequest;
import com.schediflow.dto.response.AuthResponse;
import com.schediflow.service.AuthService;
import com.schediflow.service.AuthService.LoginResult;
import com.schediflow.service.AuthService.RegistrationResult;
import com.schediflow.service.AuthService.TokenRefreshResult;
import com.schediflow.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
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

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponse.of(result.accessToken(), result.accessTokenExpiryMs() / 1000));
    }

    /**
     * POST /api/v1/auth/login
     *
     * <p>Authenticates by email/password. Returns a JWT access token in the body
     * and sets an HttpOnly refresh token cookie. Returns 401 for any credential or
     * status failure with no distinction between wrong email and wrong password.</p>
     *
     * @return 200 OK with {@link AuthResponse}; 401 for invalid credentials or INACTIVE account;
     *         429 if rate limit exceeded
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        LoginResult result = authService.login(request.email(), request.password());

        addRefreshCookie(response, result.refreshToken(), result.refreshTokenExpiryMs());

        return ResponseEntity.ok(
                AuthResponse.of(result.accessToken(), result.accessTokenExpiryMs() / 1000));
    }

    /**
     * POST /api/v1/auth/refresh
     *
     * <p>Validates the HttpOnly refresh cookie and returns a new JWT access token.
     * Refresh token is NOT rotated (post-MVP). Returns 401 for any invalid/expired/missing state.</p>
     *
     * @return 200 OK with {@link AuthResponse}; 401 if cookie missing, invalid, or expired
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {

        if (refreshToken == null) {
            throw new UnauthorizedException("Refresh token missing");
        }

        TokenRefreshResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok(
                AuthResponse.of(result.accessToken(), result.accessTokenExpiryMs() / 1000));
    }

    /**
     * POST /api/v1/auth/complete-registration
     *
     * <p>Consumes a single-use invitation token, activates the user account, and
     * auto-logs in by returning a JWT access token + HttpOnly refresh cookie.</p>
     *
     * @return 200 OK with {@link AuthResponse}; 400 if token is invalid, expired, or already used
     */
    @PostMapping("/complete-registration")
    public ResponseEntity<AuthResponse> completeRegistration(
            @Valid @RequestBody CompleteRegistrationRequest request,
            HttpServletResponse response) {

        RegistrationResult result = userService.completeRegistration(
                request.token(), request.password(), request.displayName());

        addRefreshCookie(response, result.refreshToken(), result.refreshTokenExpiryMs());

        return ResponseEntity.ok(
                AuthResponse.of(result.accessToken(), result.accessTokenExpiryMs() / 1000));
    }

    /**
     * POST /api/v1/auth/logout
     *
     * <p>Deletes the refresh token from the server and clears the HttpOnly cookie.
     * Requires a valid JWT access token. Idempotent — returns 204 even if cookie is absent.</p>
     *
     * @return 204 No Content; 401 if access token is missing or invalid
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {

        authService.logout(refreshToken);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets the refresh token as an HttpOnly cookie scoped to the refresh endpoint.
     * SameSite=Strict prevents CSRF. Secure flag is enforced at NGINX level in prod.
     */
    private void addRefreshCookie(HttpServletResponse response, String token, long expiryMs) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .path("/api/v1/auth/refresh")
                .maxAge(expiryMs / 1000)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Expires the refresh cookie immediately (max-age=0). */
    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .path("/api/v1/auth/refresh")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
