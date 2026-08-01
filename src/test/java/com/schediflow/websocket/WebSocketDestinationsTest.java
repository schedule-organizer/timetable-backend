package com.schediflow.websocket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketDestinationsTest {

    @Test
    void buildsTenantTopic() {
        assertThat(WebSocketDestinations.tenantTopic(3L)).isEqualTo("/topic/tenant/3/notifications");
    }

    @Test
    void buildsUserQueue() {
        assertThat(WebSocketDestinations.userQueue(7L)).isEqualTo("/queue/user/7/notifications");
    }

    @Test
    void parsesItsOwnDestinations() {
        assertThat(WebSocketDestinations.tenantIdOf(WebSocketDestinations.tenantTopic(3L))).isEqualTo(3L);
        assertThat(WebSocketDestinations.userIdOf(WebSocketDestinations.userQueue(7L))).isEqualTo(7L);
    }

    @Test
    void doesNotConfuseTheTwoDestinationKinds() {
        assertThat(WebSocketDestinations.userIdOf("/topic/tenant/3/notifications")).isNull();
        assertThat(WebSocketDestinations.tenantIdOf("/queue/user/7/notifications")).isNull();
    }

    @Test
    void rejectsNearMisses() {
        assertThat(WebSocketDestinations.tenantIdOf(null)).isNull();
        assertThat(WebSocketDestinations.tenantIdOf("/topic/tenant/3/notifications/extra")).isNull();
        assertThat(WebSocketDestinations.tenantIdOf("/topic/tenant/abc/notifications")).isNull();
        assertThat(WebSocketDestinations.tenantIdOf("/topic/tenant//notifications")).isNull();
        assertThat(WebSocketDestinations.userIdOf("/queue/user/7/other")).isNull();
        assertThat(WebSocketDestinations.userIdOf("prefix/queue/user/7/notifications")).isNull();
    }
}
