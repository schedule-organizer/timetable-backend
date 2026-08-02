package com.schediflow.websocket;

import com.schediflow.dto.event.CoverAssignedEvent;
import com.schediflow.dto.event.DelegationUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketEventPublisherTest {

    @Mock SimpMessagingTemplate messagingTemplate;

    WebSocketEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WebSocketEventPublisher(messagingTemplate);
    }

    @Test
    void publishToTenant_usesTenantTopic() {
        CoverAssignedEvent event = new CoverAssignedEvent(1L, 2L, 3L, OffsetDateTime.now());

        publisher.publishToTenant(42L, event);

        verify(messagingTemplate).convertAndSend("/topic/tenant/42/notifications", (Object) event);
    }

    @Test
    void publishToUser_usesPersonalQueue() {
        DelegationUpdateEvent event = new DelegationUpdateEvent(5L, "SWAP", "APPROVED", List.of(1L, 2L));

        publisher.publishToUser(9L, event);

        verify(messagingTemplate).convertAndSend("/queue/user/9/notifications", (Object) event);
    }

    @Test
    void brokerFailure_isSwallowedSoTheBusinessOperationSurvives() {
        doThrow(new MessagingException("broker down"))
                .when(messagingTemplate)
                .convertAndSend(anyString(), any(Object.class));

        assertThatCode(() -> publisher.publishToTenant(1L, "payload")).doesNotThrowAnyException();
        assertThatCode(() -> publisher.publishToUser(1L, "payload")).doesNotThrowAnyException();
    }

    @Test
    void eventPayloadsCarryTheirDiscriminator() {
        assertThat(new CoverAssignedEvent(1L, 2L, 3L, OffsetDateTime.now()).event())
                .isEqualTo("COVER_ASSIGNED");
        assertThat(new DelegationUpdateEvent(1L, "HANDOVER", "PENDING", List.of()).event())
                .isEqualTo("DELEGATION_UPDATE");
    }

    @Test
    void delegationEvent_keepsDelegationTypeSeparateFromDiscriminator() {
        DelegationUpdateEvent event = new DelegationUpdateEvent(5L, "SWAP", "REJECTED", List.of(7L));

        assertThat(event.event()).isEqualTo("DELEGATION_UPDATE");
        assertThat(event.type()).isEqualTo("SWAP");
        assertThat(event.status()).isEqualTo("REJECTED");
        assertThat(event.lessonIds()).containsExactly(7L);
    }
}
