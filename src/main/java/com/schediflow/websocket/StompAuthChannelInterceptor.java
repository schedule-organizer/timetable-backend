package com.schediflow.websocket;

import com.schediflow.repository.SolverJobRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Authenticates STOMP CONNECT frames from the {@code Authorization: Bearer …} native header and
 * authorizes SUBSCRIBE frames.
 *
 * <p>The simple broker treats {@code /queue/**} as an ordinary destination, so without the
 * subscribe check any authenticated user could listen on another user's personal queue, or on
 * another tenant's topic. Every subscription must therefore target the caller's own tenant topic or
 * own personal queue.</p>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final TimetableRepository timetableRepository;
    private final SolverJobRepository solverJobRepository;

    public StompAuthChannelInterceptor(
            JwtTokenProvider jwtTokenProvider,
            TimetableRepository timetableRepository,
            SolverJobRepository solverJobRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.timetableRepository = timetableRepository;
        this.solverJobRepository = solverJobRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String token = bearerToken(accessor);
        if (token == null) {
            throw new MessagingAuthenticationException("Missing Authorization header on CONNECT");
        }
        try {
            Claims claims = jwtTokenProvider.validateAndExtractClaims(token);
            JwtPrincipal principal = new JwtPrincipal(
                    Long.parseLong(claims.getSubject()),
                    ((Number) claims.get("tenantId")).longValue(),
                    claims.get("role", String.class),
                    claims.get("email", String.class));
            return new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        } catch (JwtException | NullPointerException | NumberFormatException | ClassCastException e) {
            throw new MessagingAuthenticationException("Invalid or expired token on CONNECT");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        JwtPrincipal principal = principalOf(accessor);
        if (principal == null) {
            throw new MessagingAuthenticationException("Not authenticated");
        }

        String destination = accessor.getDestination();
        Long tenantId = WebSocketDestinations.tenantIdOf(destination);
        if (tenantId != null) {
            if (!Objects.equals(tenantId, principal.tenantId())) {
                throw new MessagingAuthenticationException("Cannot subscribe to another tenant's topic");
            }
            return;
        }

        Long userId = WebSocketDestinations.userIdOf(destination);
        if (userId != null) {
            if (!Objects.equals(userId, principal.userId())) {
                throw new MessagingAuthenticationException("Cannot subscribe to another user's queue");
            }
            return;
        }

        // A timetable topic carries no tenant in its path, so ownership is resolved from the row.
        Long timetableId = WebSocketDestinations.timetableIdOf(destination);
        if (timetableId != null) {
            if (timetableRepository.findByIdAndTenantId(timetableId, principal.tenantId()).isEmpty()) {
                throw new MessagingAuthenticationException(
                        "Cannot subscribe to a timetable outside your institution");
            }
            return;
        }

        // Solver topics carry no tenant either; the job row decides.
        Long solverJobId = WebSocketDestinations.solverJobIdOf(destination);
        if (solverJobId != null) {
            if (solverJobRepository.findByIdAndTenantId(solverJobId, principal.tenantId()).isEmpty()) {
                throw new MessagingAuthenticationException(
                        "Cannot subscribe to a solver job outside your institution");
            }
            return;
        }

        throw new MessagingAuthenticationException("Unknown destination: " + destination);
    }

    private static JwtPrincipal principalOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof JwtPrincipal principal) {
            return principal;
        }
        return null;
    }

    private static String bearerToken(StompHeaderAccessor accessor) {
        List<String> headers = accessor.getNativeHeader("Authorization");
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String header = headers.get(0);
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
