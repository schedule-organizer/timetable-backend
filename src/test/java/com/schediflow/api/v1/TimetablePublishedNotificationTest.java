package com.schediflow.api.v1;

import com.schediflow.dto.event.TimetablePublishedEvent;
import com.schediflow.websocket.WebSocketDestinations;
import com.schediflow.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NOTIF-02: publication is announced on the tenant topic and on personal queues. */
class TimetablePublishedNotificationTest extends AbstractEndpointTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    @SpyBean WebSocketEventPublisher eventPublisher;

    private String adminToken;
    private long tenantId;
    private long adminUserId;
    private long timetableId;
    private String termName;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@notif-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);
        adminUserId = userIdOf(adminEmail);

        long termId = createTerm(adminToken, MONDAY.minusMonths(1), MONDAY.plusMonths(1));
        termName = jdbcTemplate.queryForObject(
                "SELECT name FROM terms WHERE id = ?", String.class, termId);
        timetableId = createdId(postCreated("/api/v1/timetables", adminToken, Map.of(
                "name", "Autumn Grid", "termId", termId)));
    }

    @Test
    void publishing_broadcastsAnEnrichedEventOnTheTenantTopic() throws Exception {
        mockMvc.perform(post("/api/v1/timetables/" + timetableId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishToTenant(eq(tenantId), payload.capture());

        TimetablePublishedEvent event = (TimetablePublishedEvent) payload.getValue();
        assertThat(event.event()).isEqualTo("TIMETABLE_PUBLISHED");
        assertThat(event.timetableId()).isEqualTo(timetableId);
        assertThat(event.timetableName()).isEqualTo("Autumn Grid");
        assertThat(event.termName()).isEqualTo(termName);
        assertThat(event.publishedAt()).isNotNull();
    }

    @Test
    void publishing_alsoReachesEachActiveUsersPersonalQueue() throws Exception {
        mockMvc.perform(post("/api/v1/timetables/" + timetableId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        verify(eventPublisher, atLeastOnce()).publishToUser(eq(adminUserId), any());
    }

    @Test
    void destinationsMatchTheSpecifiedFormats() {
        assertThat(WebSocketDestinations.tenantTopic(tenantId))
                .isEqualTo("/topic/tenant/" + tenantId + "/notifications");
        assertThat(WebSocketDestinations.userQueue(adminUserId))
                .isEqualTo("/queue/user/" + adminUserId + "/notifications");
    }
}
