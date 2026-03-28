package com.schediflow.service;

import com.schediflow.domain.InvitationToken;
import com.schediflow.domain.RefreshToken;
import com.schediflow.domain.User;
import com.schediflow.dto.response.PagedResponse;
import com.schediflow.dto.response.UserResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.InvitationTokenRepository;
import com.schediflow.repository.RefreshTokenRepository;
import com.schediflow.repository.UserRepository;
import com.schediflow.repository.UserSpecification;
import com.schediflow.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final InvitationTokenRepository invitationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final String frontendUrl;
    private final long refreshTokenExpiryMs;

    public UserService(UserRepository userRepository,
                       InvitationTokenRepository invitationTokenRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       EmailService emailService,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       @Value("${app.frontend-url}") String frontendUrl,
                       @Value("${app.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {
        this.userRepository = userRepository;
        this.invitationTokenRepository = invitationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.frontendUrl = frontendUrl;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    /**
     * Invites a teacher to the tenant by email.
     *
     * <ul>
     *   <li>If the email is new: creates a PENDING_REGISTRATION user and sends an invitation.</li>
     *   <li>If PENDING_REGISTRATION: invalidates old token, generates new one, re-sends invitation.</li>
     *   <li>If ACTIVE: throws {@link ConflictException}.</li>
     * </ul>
     *
     * @param tenantId the tenant of the inviting user
     * @param email    the invitee's email address
     */
    @Transactional
    public void invite(Long tenantId, String email) {
        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            User user = existing.get();
            if ("ACTIVE".equals(user.getStatus())) {
                throw new ConflictException("Email already registered");
            }
            // PENDING_REGISTRATION — invalidate old tokens and re-send
            invitationTokenRepository.deleteByUserId(user.getId());
            issueTokenAndSendEmail(user, email);
            return;
        }

        // New user
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setRole("TEACHER");
        user.setStatus("PENDING_REGISTRATION");
        user = userRepository.save(user);

        issueTokenAndSendEmail(user, email);
    }

    /**
     * Completes a teacher's registration by consuming a single-use invitation token.
     * Activates the account, sets the password, and auto-logs in with new JWT + refresh token.
     *
     * @param rawToken    the raw UUID from the invitation link
     * @param password    the desired password (will be BCrypt-hashed)
     * @param displayName optional display name (may be null)
     * @return {@link AuthService.RegistrationResult} carrying access + refresh tokens
     * @throws BadRequestException if token is missing, expired, or already used
     */
    @Transactional
    public AuthService.RegistrationResult completeRegistration(
            String rawToken, String password, String displayName) {

        String tokenHash = sha256Hex(rawToken);
        InvitationToken invitationToken = invitationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired invitation token"));

        if (invitationToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Invalid or expired invitation token");
        }

        if (invitationToken.getUsedAt() != null) {
            throw new BadRequestException("Invalid or expired invitation token");
        }

        User user = userRepository.findById(invitationToken.getUserId())
                .orElseThrow(() -> new BadRequestException("Invalid or expired invitation token"));

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus("ACTIVE");
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
        userRepository.save(user);

        invitationToken.setUsedAt(OffsetDateTime.now());
        invitationTokenRepository.save(invitationToken);

        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(OffsetDateTime.now().plusNanos(refreshTokenExpiryMs * 1_000_000L));
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtTokenProvider.generateToken(
                user.getId(), user.getTenantId(), user.getRole(), user.getEmail());

        return new AuthService.RegistrationResult(
                accessToken, refreshToken.getToken(),
                jwtTokenProvider.getAccessTokenExpiryMs(), refreshTokenExpiryMs);
    }

    private void issueTokenAndSendEmail(User user, String email) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawToken);

        InvitationToken invitationToken = new InvitationToken();
        invitationToken.setUserId(user.getId());
        invitationToken.setTokenHash(tokenHash);
        invitationToken.setExpiresAt(OffsetDateTime.now().plusHours(72));
        invitationTokenRepository.save(invitationToken);

        String inviteUrl = frontendUrl + "/complete-registration?token=" + rawToken;
        emailService.sendInvitation(email, inviteUrl);
    }

    /**
     * Returns the profile of the authenticated user.
     *
     * @param userId the caller's user ID from the JWT
     */
    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        return toResponse(user);
    }

    /**
     * Updates the authenticated user's profile.
     * Display name and/or password may be updated independently.
     * If {@code newPassword} is provided, {@code currentPassword} must match the stored hash.
     *
     * @param userId          the caller's user ID from the JWT
     * @param displayName     new display name (null = no change)
     * @param currentPassword must be supplied when changing password
     * @param newPassword     the new password to set (null = no change)
     * @return the updated profile
     */
    @Transactional
    public UserResponse updateMe(Long userId, String displayName,
                                 String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (newPassword != null && !newPassword.isBlank()) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }

        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }

        userRepository.save(user);
        return toResponse(user);
    }

    /**
     * Returns a paginated, filtered list of users within the current tenant.
     * Page size is capped at 100 regardless of the requested value.
     *
     * @param role     optional role filter (null = all roles)
     * @param status   optional status filter (null = all statuses)
     * @param pageable paging/sorting parameters (default size 20, capped at 100)
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsers(String role, String status, Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            pageable = PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort());
        }
        var spec = UserSpecification.withFilters(role, status);
        var page = userRepository.findAll(spec, pageable);
        return PagedResponse.from(page, this::toResponse);
    }

    /**
     * Soft-deactivates a user by setting their status to INACTIVE and invalidating
     * all their active refresh tokens. Cannot be used to deactivate the caller's own account.
     *
     * @param callerId the caller's user ID from the JWT
     * @param targetId the ID of the user to deactivate
     * @throws BadRequestException       if caller targets themselves
     * @throws ResourceNotFoundException if target user not found in the caller's tenant
     */
    @Transactional
    public void deactivateUser(Long callerId, Long targetId) {
        if (callerId.equals(targetId)) {
            throw new BadRequestException("Cannot deactivate own account");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus("INACTIVE");
        userRepository.save(user);
        refreshTokenRepository.deleteByUserId(targetId);
    }

    private static final Set<String> VALID_ROLES = Set.of("ADMIN", "MOD", "TEACHER");

    /**
     * Changes the role of the target user within the caller's tenant.
     * Cannot be used to change the caller's own role.
     *
     * @param callerId the caller's user ID from the JWT
     * @param targetId the ID of the user to update
     * @param role     the new role (must be ADMIN, MOD, or TEACHER)
     * @return updated user profile
     * @throws BadRequestException       if role is invalid or caller targets themselves
     * @throws ResourceNotFoundException if target user not found in the caller's tenant
     */
    @Transactional
    public UserResponse changeRole(Long callerId, Long targetId, String role) {
        if (!VALID_ROLES.contains(role)) {
            throw new BadRequestException("Invalid role: " + role + ". Must be one of: ADMIN, MOD, TEACHER");
        }
        if (callerId.equals(targetId)) {
            throw new BadRequestException("Cannot change own role");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setRole(role);
        userRepository.save(user);
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
