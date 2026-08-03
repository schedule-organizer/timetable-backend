package com.schediflow.websocket;

import com.schediflow.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** NOTIF-01: the SockJS fallback is served alongside the native endpoint. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SockJsFallbackIntegrationTest {

    @LocalServerPort int port;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired TestRestTemplate restTemplate;

    private WebSocketStompClient sockJsStompClient;
    private StompSession session;

    @BeforeEach
    void setUp() {
        sockJsStompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        sockJsStompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        sockJsStompClient.stop();
    }

    @Test
    void sockJsInfoEndpointIsServed() {
        ResponseEntity<String> info =
                restTemplate.getForEntity("http://localhost:" + port + "/ws/info", String.class);

        assertThat(info.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(info.getBody()).contains("websocket");
    }

    @Test
    void aSockJsClientCanConnectAndSubscribe() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization",
                "Bearer " + jwtTokenProvider.generateToken(7L, 3L, "MODERATOR", "mod@sockjs-test.edu"));

        session = sockJsStompClient
                .connectAsync(
                        "http://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();
    }
}
