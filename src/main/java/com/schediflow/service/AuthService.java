package com.schediflow.service;

import com.schediflow.domain.RefreshToken;
import com.schediflow.domain.Tenant;
import com.schediflow.domain.User;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.UnauthorizedException;
import com.schediflow.repository.RefreshTokenRepository;
import com.schediflow.repository.TenantRepository;
import com.schediflow.repository.UserRepository;
import com.schediflow.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final InstitutionSeedService institutionSeedService;
    private final long refreshTokenExpiryMs;

    public AuthService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       InstitutionSeedService institutionSeedService,
                       @Value("${app.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.institutionSeedService = institutionSeedService;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    /**
     * Registers a new institution (tenant) with its first Admin user.
     * Creates Tenant + User + RefreshToken in a single transaction.
     *
     * @return a {@link RegistrationResult} carrying the access token and opaque refresh token
     * @throws ConflictException if the email is already registered
     */
    @Transactional
    public RegistrationResult register(String institutionName, String email, String rawPassword) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email already registered");
        }

        Tenant tenant = new Tenant();
        tenant.setName(institutionName);
        tenant.setSlug(generateUniqueSlug(institutionName));
        tenant = tenantRepository.save(tenant);

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole("ADMIN");
        user.setStatus("ACTIVE");
        user = userRepository.save(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(OffsetDateTime.now().plusNanos(refreshTokenExpiryMs * 1_000_000L));
        refreshTokenRepository.save(refreshToken);

        institutionSeedService.seedDefaults(tenant.getId());

        String accessToken = jwtTokenProvider.generateToken(
                user.getId(), tenant.getId(), user.getRole(), user.getEmail());

        return new RegistrationResult(accessToken, refreshToken.getToken(),
                jwtTokenProvider.getAccessTokenExpiryMs(), refreshTokenExpiryMs);
    }

    /**
     * Converts an institution name to a URL-safe slug, ensuring uniqueness by
     * appending a counter when collisions occur.
     *
     * <p>Examples: "Springfield High School" → "springfield-high-school"</p>
     */
    String generateUniqueSlug(String institutionName) {
        String base = institutionName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.length() > 55) {
            base = base.substring(0, 55).replaceAll("-+$", "");
        }
        if (!tenantRepository.existsBySlug(base)) {
            return base;
        }
        int counter = 2;
        while (true) {
            String candidate = base + "-" + counter;
            if (!tenantRepository.existsBySlug(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    /**
     * Carries the result of a successful registration.
     *
     * @param accessToken          signed JWT access token
     * @param refreshToken         opaque UUID stored in the refresh_tokens table
     * @param refreshTokenExpiryMs expiry of the refresh token in milliseconds
     */
    public record RegistrationResult(String accessToken, String refreshToken,
                                      long accessTokenExpiryMs, long refreshTokenExpiryMs) {}

    /**
     * Authenticates a user by email and password.
     * Deletes any existing refresh tokens before issuing a new one.
     *
     * @throws UnauthorizedException for any credential or status failure (no hint which field failed)
     */
    @Transactional
    public LoginResult login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(OffsetDateTime.now().plusNanos(refreshTokenExpiryMs * 1_000_000L));
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtTokenProvider.generateToken(
                user.getId(), user.getTenantId(), user.getRole(), user.getEmail());

        return new LoginResult(accessToken, refreshToken.getToken(),
                jwtTokenProvider.getAccessTokenExpiryMs(), refreshTokenExpiryMs);
    }

    public record LoginResult(String accessToken, String refreshToken,
                               long accessTokenExpiryMs, long refreshTokenExpiryMs) {}

    /**
     * Issues a new JWT access token for a valid, unexpired refresh token.
     * Does NOT rotate the refresh token (post-MVP).
     *
     * @throws UnauthorizedException if the token is missing, expired, or the user is inactive
     */
    @Transactional(readOnly = true)
    public TokenRefreshResult refresh(String tokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        String accessToken = jwtTokenProvider.generateToken(
                user.getId(), user.getTenantId(), user.getRole(), user.getEmail());

        return new TokenRefreshResult(accessToken, jwtTokenProvider.getAccessTokenExpiryMs());
    }

    public record TokenRefreshResult(String accessToken, long accessTokenExpiryMs) {}

    /**
     * Deletes the server-side refresh token record.
     * Idempotent — safe to call when no token is present or it was already deleted.
     */
    @Transactional
    public void logout(String tokenValue) {
        if (tokenValue != null) {
            refreshTokenRepository.deleteByToken(tokenValue);
        }
    }
}
