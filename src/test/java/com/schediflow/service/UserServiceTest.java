package com.schediflow.service;

import com.schediflow.domain.InvitationToken;
import com.schediflow.domain.RefreshToken;
import com.schediflow.domain.User;
import com.schediflow.dto.response.PagedResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.InvitationTokenRepository;
import com.schediflow.repository.RefreshTokenRepository;
import com.schediflow.repository.UserRepository;
import com.schediflow.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock InvitationTokenRepository invitationTokenRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock EmailService emailService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, invitationTokenRepository, refreshTokenRepository,
                emailService, passwordEncoder, jwtTokenProvider,
                "http://localhost:3000", 604800000L);
    }

    // ── invite() — new user ───────────────────────────────────────────────────

    @Test
    void invite_newEmail_createsUserAndSendsEmail() {
        when(userRepository.findByEmail("teacher@school.edu")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            setUserId(u, 42L);
            return u;
        });
        when(invitationTokenRepository.save(any(InvitationToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendInvitation(any(), any());

        userService.invite(1L, "teacher@school.edu");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("teacher@school.edu");
        assertThat(saved.getRole()).isEqualTo("TEACHER");
        assertThat(saved.getStatus()).isEqualTo("PENDING_REGISTRATION");
        assertThat(saved.getTenantId()).isEqualTo(1L);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvitation(eq("teacher@school.edu"), urlCaptor.capture());
        assertThat(urlCaptor.getValue()).startsWith("http://localhost:3000/complete-registration?token=");
    }

    // ── invite() — ACTIVE user → 409 ─────────────────────────────────────────

    @Test
    void invite_activeEmail_throwsConflict() {
        User active = new User();
        active.setEmail("active@school.edu");
        active.setStatus("ACTIVE");
        when(userRepository.findByEmail("active@school.edu")).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> userService.invite(1L, "active@school.edu"))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendInvitation(any(), any());
    }

    // ── invite() — PENDING_REGISTRATION → re-send ────────────────────────────

    @Test
    void invite_pendingEmail_deletesOldTokenAndResends() {
        User pending = new User();
        setUserId(pending, 7L);
        pending.setEmail("pending@school.edu");
        pending.setStatus("PENDING_REGISTRATION");
        when(userRepository.findByEmail("pending@school.edu")).thenReturn(Optional.of(pending));
        doNothing().when(invitationTokenRepository).deleteByUserId(7L);
        when(invitationTokenRepository.save(any(InvitationToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendInvitation(any(), any());

        userService.invite(1L, "pending@school.edu");

        verify(invitationTokenRepository).deleteByUserId(7L);
        verify(invitationTokenRepository).save(any(InvitationToken.class));
        verify(emailService).sendInvitation(eq("pending@school.edu"), any());
        verify(userRepository, never()).save(any());
    }

    // ── invite() — token hash correctness ────────────────────────────────────

    @Test
    void invite_tokenHashIsSha256OfRawToken() {
        when(userRepository.findByEmail("hash@school.edu")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            setUserId(u, 1L);
            return u;
        });
        when(invitationTokenRepository.save(any(InvitationToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(emailService).sendInvitation(any(), urlCaptor.capture());

        userService.invite(1L, "hash@school.edu");

        String url = urlCaptor.getValue();
        String rawToken = url.substring(url.indexOf("token=") + 6);

        ArgumentCaptor<InvitationToken> tokenCaptor = ArgumentCaptor.forClass(InvitationToken.class);
        verify(invitationTokenRepository).save(tokenCaptor.capture());
        String storedHash = tokenCaptor.getValue().getTokenHash();

        assertThat(storedHash).isEqualTo(UserService.sha256Hex(rawToken));
        assertThat(storedHash).hasSize(64);
    }

    // ── invite() — token expires in 72 hours ─────────────────────────────────

    @Test
    void invite_tokenExpiresIn72Hours() {
        when(userRepository.findByEmail("exp@school.edu")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            setUserId(u, 2L);
            return u;
        });
        when(invitationTokenRepository.save(any(InvitationToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendInvitation(any(), any());

        userService.invite(1L, "exp@school.edu");

        ArgumentCaptor<InvitationToken> captor = ArgumentCaptor.forClass(InvitationToken.class);
        verify(invitationTokenRepository).save(captor.capture());

        var expiresAt = captor.getValue().getExpiresAt();
        assertThat(expiresAt).isAfter(OffsetDateTime.now().plusHours(71));
        assertThat(expiresAt).isBefore(OffsetDateTime.now().plusHours(73));
    }

    // ── completeRegistration() — happy path ───────────────────────────────────

    @Test
    void completeRegistration_validToken_activatesUserAndReturnsTokens() {
        String rawToken = "test-raw-uuid";
        String hash = UserService.sha256Hex(rawToken);

        InvitationToken token = new InvitationToken();
        setTokenId(token, 5L);
        token.setUserId(10L);
        token.setTokenHash(hash);
        token.setExpiresAt(OffsetDateTime.now().plusHours(24));

        User user = new User();
        setUserId(user, 10L);
        user.setEmail("teacher@school.edu");
        user.setRole("TEACHER");
        user.setStatus("PENDING_REGISTRATION");
        user.setTenantId(1L);

        when(invitationTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invitationTokenRepository.save(any(InvitationToken.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(refreshTokenRepository).deleteByUserId(10L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(jwtTokenProvider.generateToken(10L, 1L, "TEACHER", "teacher@school.edu"))
                .thenReturn("access.jwt");
        when(jwtTokenProvider.getAccessTokenExpiryMs()).thenReturn(900000L);

        AuthService.RegistrationResult result =
                userService.completeRegistration(rawToken, "Password1", "Ms Smith");

        assertThat(result.accessToken()).isEqualTo("access.jwt");
        assertThat(result.refreshToken()).isNotBlank();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("Ms Smith");

        ArgumentCaptor<InvitationToken> tokenCaptor = ArgumentCaptor.forClass(InvitationToken.class);
        verify(invitationTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();
    }

    // ── completeRegistration() — invalid token ────────────────────────────────

    @Test
    void completeRegistration_unknownToken_throwsBadRequest() {
        when(invitationTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.completeRegistration("bad-token", "Password1", null))
                .isInstanceOf(BadRequestException.class);
    }

    // ── completeRegistration() — expired token ────────────────────────────────

    @Test
    void completeRegistration_expiredToken_throwsBadRequest() {
        String rawToken = "expired-uuid";
        String hash = UserService.sha256Hex(rawToken);

        InvitationToken token = new InvitationToken();
        token.setUserId(10L);
        token.setTokenHash(hash);
        token.setExpiresAt(OffsetDateTime.now().minusHours(1));

        when(invitationTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> userService.completeRegistration(rawToken, "Password1", null))
                .isInstanceOf(BadRequestException.class);
    }

    // ── completeRegistration() — already used token ───────────────────────────

    @Test
    void completeRegistration_alreadyUsedToken_throwsBadRequest() {
        String rawToken = "used-uuid";
        String hash = UserService.sha256Hex(rawToken);

        InvitationToken token = new InvitationToken();
        token.setUserId(10L);
        token.setTokenHash(hash);
        token.setExpiresAt(OffsetDateTime.now().plusHours(24));
        token.setUsedAt(OffsetDateTime.now().minusHours(1));

        when(invitationTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> userService.completeRegistration(rawToken, "Password1", null))
                .isInstanceOf(BadRequestException.class);
    }

    // ── listUsers() ───────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_noFilters_returnsAllUsers() {
        User u1 = makeUser(1L, "a@school.edu", "TEACHER", "ACTIVE");
        User u2 = makeUser(2L, "b@school.edu", "ADMIN", "ACTIVE");
        var springPage = new PageImpl<User>(List.of(u1, u2), PageRequest.of(0, 20), 2);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(springPage);

        PagedResponse<com.schediflow.dto.response.UserResponse> result =
                userService.listUsers(null, null, PageRequest.of(0, 20));

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_roleFilter_passesSpecToRepository() {
        var springPage = new PageImpl<User>(List.of(makeUser(1L, "t@school.edu", "TEACHER", "ACTIVE")),
                PageRequest.of(0, 20), 1);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(springPage);

        PagedResponse<com.schediflow.dto.response.UserResponse> result =
                userService.listUsers("TEACHER", null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).role()).isEqualTo("TEACHER");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_oversizedPage_capsAt100() {
        var springPage = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(springPage);

        userService.listUsers(null, null, PageRequest.of(0, 500));

        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    // ── getMe() ───────────────────────────────────────────────────────────────

    @Test
    void getMe_returnsUserResponse() {
        User user = new User();
        setUserId(user, 5L);
        user.setEmail("me@school.edu");
        user.setRole("ADMIN");
        user.setStatus("ACTIVE");
        user.setDisplayName("Alice");
        user.setTenantId(1L);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        var response = userService.getMe(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.email()).isEqualTo("me@school.edu");
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    // ── updateMe() ────────────────────────────────────────────────────────────

    @Test
    void updateMe_displayNameOnly_updatesName() {
        User user = new User();
        setUserId(user, 5L);
        user.setEmail("me@school.edu");
        user.setRole("TEACHER");
        user.setStatus("ACTIVE");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.updateMe(5L, "New Name", null, null);

        assertThat(response.displayName()).isEqualTo("New Name");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void updateMe_passwordChange_correctCurrentPassword_succeeds() {
        User user = new User();
        setUserId(user, 5L);
        user.setEmail("me@school.edu");
        user.setRole("TEACHER");
        user.setStatus("ACTIVE");
        user.setPasswordHash("old-hash");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateMe(5L, null, "OldPass1", "NewPass1");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void updateMe_passwordChange_wrongCurrentPassword_throwsBadRequest() {
        User user = new User();
        setUserId(user, 5L);
        user.setEmail("me@school.edu");
        user.setRole("TEACHER");
        user.setStatus("ACTIVE");
        user.setPasswordHash("old-hash");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateMe(5L, null, "WrongPass", "NewPass1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    // ── deactivateUser() ──────────────────────────────────────────────────────

    @Test
    void deactivateUser_success_setsInactiveAndDeletesTokens() {
        User target = makeUser(30L, "teacher@school.edu", "TEACHER", "ACTIVE");
        when(userRepository.findById(30L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.deactivateUser(1L, 30L);

        assertThat(target.getStatus()).isEqualTo("INACTIVE");
        verify(userRepository).save(target);
        verify(refreshTokenRepository).deleteByUserId(30L);
    }

    @Test
    void deactivateUser_selfDeactivation_throwsBadRequest() {
        assertThatThrownBy(() -> userService.deactivateUser(5L, 5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot deactivate own account");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void deactivateUser_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    // ── changeRole() ──────────────────────────────────────────────────────────

    @Test
    void changeRole_success_updatesRoleAndReturnsResponse() {
        User target = makeUser(20L, "teacher@school.edu", "TEACHER", "ACTIVE");
        when(userRepository.findById(20L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.changeRole(1L, 20L, "MOD");

        assertThat(response.role()).isEqualTo("MOD");
        verify(userRepository).save(target);
    }

    @Test
    void changeRole_selfChange_throwsBadRequest() {
        assertThatThrownBy(() -> userService.changeRole(5L, 5L, "MOD"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot change own role");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void changeRole_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeRole(1L, 99L, "MOD"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changeRole_invalidRole_throwsBadRequest() {
        assertThatThrownBy(() -> userService.changeRole(1L, 20L, "SUPERUSER"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid role");
        verify(userRepository, never()).findById(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User makeUser(long id, String email, String role, String status) {
        User u = new User();
        setUserId(u, id);
        u.setEmail(email);
        u.setRole(role);
        u.setStatus(status);
        u.setTenantId(1L);
        return u;
    }

    private void setUserId(User user, long id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void setTokenId(InvitationToken token, long id) {
        try {
            var f = InvitationToken.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(token, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
