package com.schediflow.websocket;

import com.schediflow.dto.event.CoverAssignedEvent;
import com.schediflow.dto.event.DelegationUpdateEvent;
import com.schediflow.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.ConnectionLostException;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end STOMP checks over a real server: CONNECT requires a valid JWT, SUBSCRIBE is confined to
 * the caller's own tenant topic and personal queue, and published events reach subscribers (COVER-07).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoverWebSocketIntegrationTest {

    private static final long TENANT_ID = 3L;
    private static final long USER_ID = 7L;
    private static final int TIMEOUT_SECONDS = 5;

    @LocalServerPort int port;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired WebSocketEventPublisher publisher;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        stompClient.stop();
    }

    @Test
    void tenantEventReachesSubscriberOnItsOwnTopic() throws Exception {
        session = connect(token(USER_ID, TENANT_ID, "MOD"));
        BlockingQueue<Map<String, Object>> received =
                subscribe(session, WebSocketDestinations.tenantTopic(TENANT_ID));

        publisher.publishToTenant(
                TENANT_ID, new CoverAssignedEvent(11L, 22L, 33L, OffsetDateTime.now()));

        Map<String, Object> payload = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload.get("event")).isEqualTo("COVER_ASSIGNED");
        assertThat(((Number) payload.get("lessonId")).longValue()).isEqualTo(11L);
        assertThat(((Number) payload.get("coverTeacherId")).longValue()).isEqualTo(22L);
        assertThat(((Number) payload.get("originalTeacherId")).longValue()).isEqualTo(33L);
    }

    @Test
    void delegationEventReachesTheTargetedPersonalQueue() throws Exception {
        session = connect(token(USER_ID, TENANT_ID, "TEACHER"));
        BlockingQueue<Map<String, Object>> received =
                subscribe(session, WebSocketDestinations.userQueue(USER_ID));

        publisher.publishToUser(USER_ID, new DelegationUpdateEvent(5L, "SWAP", "APPROVED", List.of(1L, 2L)));

        Map<String, Object> payload = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload.get("event")).isEqualTo("DELEGATION_UPDATE");
        assertThat(payload.get("type")).isEqualTo("SWAP");
        assertThat(payload.get("status")).isEqualTo("APPROVED");
    }

    @Test
    void eventForAnotherTenantIsNotDelivered() throws Exception {
        session = connect(token(USER_ID, TENANT_ID, "MOD"));
        BlockingQueue<Map<String, Object>> received =
                subscribe(session, WebSocketDestinations.tenantTopic(TENANT_ID));

        publisher.publishToTenant(TENANT_ID + 1, new CoverAssignedEvent(11L, 22L, 33L, OffsetDateTime.now()));

        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    }

    /**
     * The server closes the socket rather than replying with the reason, so the client only ever
     * sees {@link ConnectionLostException} — not leaking why authentication failed is deliberate.
     * What matters is that no session is ever established.
     */
    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(ConnectionLostException.class);
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        assertThatThrownBy(() -> connect("not-a-jwt"))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(ConnectionLostException.class);
    }

    @Test
    void connectWithExpiredTokenIsRejected() {
        JwtTokenProvider expired = new JwtTokenProvider(
                "Y2hhbmdlLXRoaXMtaW4tcHJvZHVjdGlvbi1hdC1sZWFzdC0zMi1ieXRlcw==", -1_000L);

        assertThatThrownBy(() -> connect(expired.generateToken(USER_ID, TENANT_ID, "MOD", "x@y.edu")))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(ConnectionLostException.class);
    }

    @Test
    void subscribingToAnotherTenantsTopicClosesTheSession() throws Exception {
        session = connect(token(USER_ID, TENANT_ID, "MOD"));

        session.subscribe(WebSocketDestinations.tenantTopic(TENANT_ID + 1), new CapturingHandler(
                new LinkedBlockingQueue<>()));

        assertThat(disconnectedWithin(TIMEOUT_SECONDS)).isTrue();
    }

    @Test
    void subscribingToAnotherUsersQueueClosesTheSession() throws Exception {
        session = connect(token(USER_ID, TENANT_ID, "TEACHER"));

        session.subscribe(WebSocketDestinations.userQueue(USER_ID + 1), new CapturingHandler(
                new LinkedBlockingQueue<>()));

        assertThat(disconnectedWithin(TIMEOUT_SECONDS)).isTrue();
    }

    // ---------- helpers ----------

    private String token(long userId, long tenantId, String role) {
        return jwtTokenProvider.generateToken(userId, tenantId, role, "user" + userId + "@ws-test.edu");
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return stompClient
                .connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination)
            throws InterruptedException {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new CapturingHandler(received));
        // The SUBSCRIBE frame is in flight; give the broker a moment to register it before publishing.
        Thread.sleep(300);
        return received;
    }

    private boolean disconnectedWithin(int seconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!session.isConnected()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static final class CapturingHandler implements StompFrameHandler {

        private final BlockingQueue<Map<String, Object>> sink;

        private CapturingHandler(BlockingQueue<Map<String, Object>> sink) {
            this.sink = sink;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            if (payload instanceof Map<?, ?> map) {
                sink.add((Map<String, Object>) map);
            }
        }
    }
}
