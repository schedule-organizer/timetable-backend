package com.schediflow.service;

import com.schediflow.domain.RefreshToken;
import com.schediflow.domain.Tenant;
import com.schediflow.domain.User;
import com.schediflow.exception.ConflictException;
import com.schediflow.repository.RefreshTokenRepository;
import com.schediflow.repository.TenantRepository;
import com.schediflow.repository.UserRepository;
import com.schediflow.security.JwtTokenProvider;
import com.schediflow.service.AuthService.RegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock TenantRepository tenantRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock InstitutionSeedService institutionSeedService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, tenantRepository, refreshTokenRepository,
                passwordEncoder, jwtTokenProvider, institutionSeedService,
                604800000L);
    }

    // ── register() ────────────────────────────────────────────────────────────

    @Test
    void register_success_returnAccessTokenAndCallsSeed() {
        when(userRepository.findByEmail("admin@springfield.edu")).thenReturn(Optional.empty());

        Tenant savedTenant = new Tenant();
        setId(savedTenant, 10L);
        savedTenant.setSlug("springfield-high-school");
        savedTenant.setName("Springfield High School");
        when(tenantRepository.existsBySlug("springfield-high-school")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);

        User savedUser = new User();
        setUserId(savedUser, 99L);
        savedUser.setEmail("admin@springfield.edu");
        savedUser.setRole("ADMIN");
        savedUser.setStatus("ACTIVE");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(99L, 10L, "ADMIN", "admin@springfield.edu"))
                .thenReturn("mock.jwt.token");
        when(jwtTokenProvider.getAccessTokenExpiryMs()).thenReturn(900000L);

        RegistrationResult result = authService.register(
                "Springfield High School", "admin@springfield.edu", "password1");

        assertThat(result.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.accessTokenExpiryMs()).isEqualTo(900000L);
        verify(institutionSeedService).seedDefaults(10L);
    }

    @Test
    void register_duplicateEmail_throwsConflictException() {
        User existing = new User();
        when(userRepository.findByEmail("dup@school.edu")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                authService.register("Any School", "dup@school.edu", "password1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already registered");

        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_createsUserWithAdminRoleAndActiveStatus() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);

        Tenant t = new Tenant(); setId(t, 1L);
        when(tenantRepository.save(any())).thenReturn(t);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Return a user with role/email set so JWT generation stub matches
        User u = new User(); setUserId(u, 5L);
        u.setEmail("a@b.com"); u.setRole("ADMIN");
        when(userRepository.save(any(User.class))).thenReturn(u);
        when(jwtTokenProvider.generateToken(5L, 1L, "ADMIN", "a@b.com"))
                .thenReturn("token");
        when(jwtTokenProvider.getAccessTokenExpiryMs()).thenReturn(900000L);

        authService.register("Test School", "a@b.com", "pass1234");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getRole()).isEqualTo("ADMIN");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void register_passwordIsHashedNotStoredPlaintext() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);

        Tenant t = new Tenant(); setId(t, 1L);
        when(tenantRepository.save(any())).thenReturn(t);
        when(passwordEncoder.encode("mySecret1")).thenReturn("$2a$hashed");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User u = new User(); setUserId(u, 5L);
        u.setEmail("x@x.com"); u.setRole("ADMIN");
        when(userRepository.save(any(User.class))).thenReturn(u);
        when(jwtTokenProvider.generateToken(5L, 1L, "ADMIN", "x@x.com"))
                .thenReturn("token");
        when(jwtTokenProvider.getAccessTokenExpiryMs()).thenReturn(900000L);

        authService.register("School", "x@x.com", "mySecret1");

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(cap.getValue().getPasswordHash()).doesNotContain("mySecret1");
    }

    // ── generateUniqueSlug() ───────────────────────────────────────────────────

    @Test
    void generateUniqueSlug_simpleCase() {
        when(tenantRepository.existsBySlug("springfield-high-school")).thenReturn(false);
        assertThat(authService.generateUniqueSlug("Springfield High School"))
                .isEqualTo("springfield-high-school");
    }

    @Test
    void generateUniqueSlug_collision_appendsCounter() {
        when(tenantRepository.existsBySlug("abc-school")).thenReturn(true);
        when(tenantRepository.existsBySlug("abc-school-2")).thenReturn(true);
        when(tenantRepository.existsBySlug("abc-school-3")).thenReturn(false);
        assertThat(authService.generateUniqueSlug("ABC School"))
                .isEqualTo("abc-school-3");
    }

    @Test
    void generateUniqueSlug_specialCharsCollapsed() {
        // apostrophe splits "mary's" into "mary-s"; trailing "!!!" becomes trailing "-" then trimmed
        when(tenantRepository.existsBySlug("st-mary-s-college")).thenReturn(false);
        assertThat(authService.generateUniqueSlug("St. Mary's College!!!"))
                .isEqualTo("st-mary-s-college");
    }

    @Test
    void generateUniqueSlug_longName_baseSlugWithin63Chars() {
        String longName = "A".repeat(80) + " School";
        when(tenantRepository.existsBySlug(anyString())).thenReturn(false);
        String slug = authService.generateUniqueSlug(longName);
        assertThat(slug.length()).isLessThanOrEqualTo(63);
    }

    @Test
    void generateUniqueSlug_longNameWithCollision_candidateStaysWithin63Chars() {
        // base will be 55 a's; first collision forces "-2" suffix → 57 chars ≤ 63
        String longName = "A".repeat(80) + " School";
        when(tenantRepository.existsBySlug(anyString()))
                .thenReturn(true)   // base collides
                .thenReturn(false); // "-2" is free
        String slug = authService.generateUniqueSlug(longName);
        assertThat(slug.length()).isLessThanOrEqualTo(63);
        assertThat(slug).endsWith("-2");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setId(Tenant tenant, long id) {
        try {
            var f = Tenant.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(tenant, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void setUserId(User user, long id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
