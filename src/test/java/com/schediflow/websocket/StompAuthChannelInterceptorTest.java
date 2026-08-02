package com.schediflow.websocket;

import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompAuthChannelInterceptorTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC0zMi1ieXRlcw==";

    private JwtTokenProvider jwtTokenProvider;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 900_000L);
        interceptor = new StompAuthChannelInterceptor(jwtTokenProvider);
    }

    // ---------- CONNECT ----------

    @Test
    void connect_withValidToken_setsPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token(7L, 3L, "TEACHER"));

        Message<?> result = interceptor.preSend(message(accessor), null);

        StompHeaderAccessor out = StompHeaderAccessor.wrap(result);
        assertThat(out.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        JwtPrincipal principal =
                (JwtPrincipal) ((UsernamePasswordAuthenticationToken) out.getUser()).getPrincipal();
        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.tenantId()).isEqualTo(3L);
        assertThat(principal.role()).isEqualTo("TEACHER");
    }

    @Test
    void connect_withoutAuthorizationHeader_isRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingAuthenticationException.class)
                .hasMessageContaining("Missing Authorization");
    }

    @Test
    void connect_withNonBearerHeader_isRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Basic abc123");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingAuthenticationException.class);
    }

    @Test
    void connect_withGarbageToken_isRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer not-a-jwt");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingAuthenticationException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void connect_withExpiredToken_isRejected() {
        JwtTokenProvider expiring = new JwtTokenProvider(SECRET, -1_000L);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + expiring.generateToken(7L, 3L, "TEACHER", "t@x.edu"));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingAuthenticationException.class);
    }

    @Test
    void connect_withTokenSignedByAnotherSecret_isRejected() {
        JwtTokenProvider other =
                new JwtTokenProvider("b3RoZXItc2VjcmV0LXRoYXQtaXMtbG9uZy1lbm91Z2gtMzItYnl0ZXM=", 900_000L);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + other.generateToken(7L, 3L, "TEACHER", "t@x.edu"));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingAuthenticationException.class);
    }

    // ---------- SUBSCRIBE ----------

    @Test
    void subscribe_toOwnTenantTopic_isAllowed() {
        Message<?> message = subscribeMessage(authenticated(7L, 3L), "/topic/tenant/3/notifications");

        assertThat(interceptor.preSend(message, null)).isNotNull();
    }

    @Test
    void subscribe_toOwnUserQueue_isAllowed() {
        Message<?> message = subscribeMessage(authenticated(7L, 3L), "/queue/user/7/notifications");

        assertThat(interceptor.preSend(message, null)).isNotNull();
    }

    @Test
    void subscribe_toAnotherTenantsTopic_isRejected() {
        Message<?> message = subscribeMessage(authenticated(7L, 3L), "/topic/tenant/4/notifications");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingAuthenticationException.class)
                .hasMessageContaining("another tenant");
    }

    @Test
    void subscribe_toAnotherUsersQueue_isRejected() {
        Message<?> message = subscribeMessage(authenticated(7L, 3L), "/queue/user/8/notifications");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingAuthenticationException.class)
                .hasMessageContaining("another user");
    }

    @Test
    void subscribe_toUnknownDestination_isRejected() {
        Message<?> message = subscribeMessage(authenticated(7L, 3L), "/topic/everything");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingAuthenticationException.class)
                .hasMessageContaining("Unknown destination");
    }

    @Test
    void subscribe_withoutAuthenticatedSession_isRejected() {
        Message<?> message = subscribeMessage(null, "/topic/tenant/3/notifications");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingAuthenticationException.class)
                .hasMessageContaining("Not authenticated");
    }

    @Test
    void otherCommands_passThroughUntouched() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Message<?> message = message(accessor);

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    // ---------- helpers ----------

    private String token(Long userId, Long tenantId, String role) {
        return jwtTokenProvider.generateToken(userId, tenantId, role, "user" + userId + "@x.edu");
    }

    private static UsernamePasswordAuthenticationToken authenticated(Long userId, Long tenantId) {
        JwtPrincipal principal = new JwtPrincipal(userId, tenantId, "TEACHER", "t@x.edu");
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    private static Message<?> subscribeMessage(UsernamePasswordAuthenticationToken user, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(user);
        return message(accessor);
    }

    private static Message<?> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
