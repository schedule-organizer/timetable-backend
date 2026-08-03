package com.schediflow.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** EXPORT-08: the @Audited advice populates the trail, and the endpoint reads it back. */
class AuditLogEndpointTest extends AbstractEndpointTest {

    private static final String URL = "/api/v1/audit-log";
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long adminUserId;
    private long termId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@audit-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);
        adminUserId = userIdOf(adminEmail);

        modToken = createModUser(adminToken, "mod+" + UUID.randomUUID() + "@audit-test.edu");
        teacherToken = inviteTeacher(adminToken, "t+" + UUID.randomUUID() + "@audit-test.edu");
        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-audit.edu");

        termId = createTerm(adminToken, MONDAY.minusMonths(1), MONDAY.plusMonths(1));
    }

    @Test
    void publishing_writesAnAuditEntryNamingActorAndEntity() throws Exception {
        long timetableId = newTimetable();

        mockMvc.perform(post("/api/v1/timetables/" + timetableId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("PUBLISH"))
                .andExpect(jsonPath("$.content[0].entityType").value("Timetable"))
                .andExpect(jsonPath("$.content[0].entityId").value(timetableId))
                .andExpect(jsonPath("$.content[0].actorId").value(adminUserId))
                .andExpect(jsonPath("$.content[0].occurredAt").exists());
    }

    @Test
    void deletingADraft_isAudited() throws Exception {
        long timetableId = newTimetable();

        mockMvc.perform(delete("/api/v1/timetables/" + timetableId)
                        .header("Authorization", "Bearer " + modToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("entityType", "Timetable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("DELETE"));
    }

    @Test
    void aRejectedOperationIsNotAudited() throws Exception {
        long timetableId = newTimetable();
        mockMvc.perform(post("/api/v1/timetables/" + timetableId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        // Publishing again is a 400; the trail must not gain a second PUBLISH.
        mockMvc.perform(post("/api/v1/timetables/" + timetableId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void entriesAreScopedToTheTenant() throws Exception {
        long timetableId = newTimetable();
        mockMvc.perform(post("/api/v1/timetables/" + timetableId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByActorEntityTypeAndDateRange() throws Exception {
        long timetableId = newTimetable();
        mockMvc.perform(post("/api/v1/timetables/" + timetableId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("actorId", String.valueOf(adminUserId)))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("actorId", "999999999"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("entityType", "Room"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + adminToken)
                        .param("endDate", LocalDate.now().minusDays(1).toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void detailsSummariseArgumentsWithoutSerialisingPayloads() throws Exception {
        long timetableId = newTimetable();
        mockMvc.perform(post("/api/v1/timetables/" + timetableId + "/publish")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        String details = jdbcTemplate.queryForObject(
                "SELECT details FROM audit_log WHERE tenant_id = ?", String.class, tenantId);
        assertThat(details).contains(String.valueOf(timetableId));
        // The request object is named by type, never dumped.
        assertThat(details).doesNotContain("publishAt");
    }

    @Test
    void asModerator_returns403() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void asTeacher_returns403() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private long newTimetable() throws Exception {
        return createdId(postCreated("/api/v1/timetables", adminToken, Map.of(
                "name", "TT " + UUID.randomUUID(), "termId", termId)));
    }
}
