package com.schediflow.api.v1;

import com.schediflow.service.TimetablePublishJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCHED-07 publish, including future-dated publication and its sweep. */
class TimetablePublishEndpointTest extends AbstractEndpointTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    @Autowired TimetablePublishJob publishJob;

    private String adminToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long termId;
    private long timetableId;
    private long subjectId;
    private long classAId;
    private long classBId;
    private long teacherUserId;
    private List<Long> periods;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@pub-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);
        teacherToken = inviteTeacher(adminToken, "t+" + UUID.randomUUID() + "@pub-test.edu");
        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-pub.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        classAId = createClass(adminToken, "8A");
        classBId = createClass(adminToken, "8B");
        teacherUserId = inviteAndGetUserId(adminToken, "lt+" + UUID.randomUUID() + "@pub-test.edu");
        periods = periodIds(adminToken);

        termId = createTerm(adminToken, MONDAY.minusMonths(1), MONDAY.plusMonths(1));
        timetableId = newTimetable();
    }

    @Test
    void post_publish_setsPublishedAndTimestamp() throws Exception {
        insertLesson(tenantId, timetableId, subjectId, classAId, teacherUserId, periods.get(0), MONDAY);

        mockMvc.perform(publish(timetableId, adminToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").exists());
    }

    @Test
    void post_publish_emptyTimetable_isAllowed() throws Exception {
        mockMvc.perform(publish(timetableId, adminToken, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void post_publish_withHardViolations_returns400WithDetail() throws Exception {
        // Same teacher, two classes, one period — a teacher double-booking.
        insertLesson(tenantId, timetableId, subjectId, classAId, teacherUserId, periods.get(0), MONDAY);
        insertLesson(tenantId, timetableId, subjectId, classBId, teacherUserId, periods.get(0), MONDAY);

        mockMvc.perform(publish(timetableId, adminToken, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("unresolved conflicts")));

        assertThat(statusOf(timetableId)).isEqualTo("DRAFT");
    }

    @Test
    void post_publish_archivesThePreviouslyPublishedTimetable() throws Exception {
        mockMvc.perform(publish(timetableId, adminToken, null)).andExpect(status().isOk());
        long second = newTimetable();

        mockMvc.perform(publish(second, adminToken, null)).andExpect(status().isOk());

        assertThat(statusOf(timetableId)).isEqualTo("ARCHIVED");
        assertThat(statusOf(second)).isEqualTo("PUBLISHED");
        Integer published = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM timetables WHERE term_id = ? AND status = 'PUBLISHED'",
                Integer.class, termId);
        assertThat(published).isEqualTo(1);
    }

    @Test
    void post_publish_withFutureDate_staysDraftUntilSwept() throws Exception {
        mockMvc.perform(publish(timetableId, adminToken, OffsetDateTime.now().plusHours(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishAt").exists());

        assertThat(publishJob.publishDueTimetables()).isZero();
        assertThat(statusOf(timetableId)).isEqualTo("DRAFT");
    }

    @Test
    void scheduledSweep_publishesTimetablesWhoseTimeHasCome() throws Exception {
        mockMvc.perform(publish(timetableId, adminToken, OffsetDateTime.now().plusHours(2)))
                .andExpect(status().isOk());
        // Pull the scheduled instant into the past, as the clock would.
        jdbcTemplate.update("UPDATE timetables SET publish_at = ? WHERE id = ?",
                java.sql.Timestamp.from(OffsetDateTime.now().minusMinutes(1).toInstant()), timetableId);

        assertThat(publishJob.publishDueTimetables()).isEqualTo(1);
        assertThat(statusOf(timetableId)).isEqualTo("PUBLISHED");

        // Idempotent: publish_at is cleared, so a second sweep does nothing.
        assertThat(publishJob.publishDueTimetables()).isZero();
    }

    @Test
    void post_publish_pastDate_publishesImmediately() throws Exception {
        mockMvc.perform(publish(timetableId, adminToken, OffsetDateTime.now().minusHours(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void post_publish_alreadyPublished_returns400() throws Exception {
        mockMvc.perform(publish(timetableId, adminToken, null)).andExpect(status().isOk());

        mockMvc.perform(publish(timetableId, adminToken, null)).andExpect(status().isBadRequest());
    }

    @Test
    void post_publish_unknownTimetable_returns404() throws Exception {
        mockMvc.perform(publish(999_999_999L, adminToken, null)).andExpect(status().isNotFound());
    }

    @Test
    void post_publish_crossTenant_returns404() throws Exception {
        mockMvc.perform(publish(timetableId, otherTenantAdminToken, null)).andExpect(status().isNotFound());
    }

    @Test
    void post_publish_asTeacher_returns403() throws Exception {
        mockMvc.perform(publish(timetableId, teacherToken, null)).andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private long newTimetable() throws Exception {
        return createdId(postCreated("/api/v1/timetables", adminToken, Map.of(
                "name", "TT " + UUID.randomUUID(), "termId", termId)));
    }

    private org.springframework.test.web.servlet.RequestBuilder publish(
            long id, String token, OffsetDateTime publishAt) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("publishAt", publishAt == null ? null : publishAt.toString());
        return post("/api/v1/timetables/" + id + "/publish")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }

    private String statusOf(long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM timetables WHERE id = ?", String.class, id);
    }
}
