package com.schediflow.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes application events over STOMP.
 *
 * <p>Publishing is best-effort: a broker failure must never roll back or fail the business
 * operation that produced the event, so send errors are logged and swallowed.</p>
 */
@Component
public class WebSocketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Broadcasts to everyone subscribed to the tenant's notification topic. */
    public void publishToTenant(Long tenantId, Object payload) {
        send(WebSocketDestinations.tenantTopic(tenantId), payload);
    }

    /** Sends to one user's personal queue. */
    public void publishToUser(Long userId, Object payload) {
        send(WebSocketDestinations.userQueue(userId), payload);
    }

    /**
     * Broadcasts to an arbitrary topic already built by {@link WebSocketDestinations}. Callers must
     * use that class rather than hand-building a destination, so subscription authorization and
     * publishing can never drift apart.
     */
    public void publishToTopic(String destination, Object payload) {
        send(destination, payload);
    }

    private void send(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (RuntimeException e) {
            log.warn("Failed to publish WebSocket event to {}", destination, e);
        }
    }
}
