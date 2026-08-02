package com.schediflow.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCHED-08 (move), SCHED-09 (pin) and SCHED-10 (swap). */
class LessonMutationEndpointTest extends AbstractEndpointTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    private String adminToken;
    private String teacherAToken;
    private String teacherBToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long timetableId;
    private long subjectId;
    private long classAId;
    private long classBId;
    private long roomId;
    private long smallRoomId;
    private long teacherAUserId;
    private long teacherBUserId;
    private List<Long> periods;
    private long lessonId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@lesson-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        String aEmail = "ta+" + UUID.randomUUID() + "@lesson-test.edu";
        teacherAUserId = inviteAndGetUserId(adminToken, aEmail);
        teacherAToken = loginAndGetToken(aEmail, PASSWORD);

        String bEmail = "tb+" + UUID.randomUUID() + "@lesson-test.edu";
        teacherBUserId = inviteAndGetUserId(adminToken, bEmail);
        teacherBToken = loginAndGetToken(bEmail, PASSWORD);

        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-lesson.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        classAId = createClass(adminToken, "8A");
        classBId = createClass(adminToken, "8B");
        roomId = createRoom(40);
        smallRoomId = createRoom(2);
        createTeacherProfile(adminToken, teacherAUserId, "Ann");
        createTeacherProfile(adminToken, teacherBUserId, "Ben");
        periods = periodIds(adminToken);

        long termId = createTerm(adminToken, MONDAY.minusMonths(1), MONDAY.plusMonths(1));
        timetableId = createdId(postCreated("/api/v1/timetables", adminToken, Map.of(
                "name", "TT " + UUID.randomUUID(), "termId", termId)));
        lessonId = lesson(classAId, teacherAUserId, periods.get(0), null);
    }

    // ---------- SCHED-08 move ----------

    @Test
    void patch_movesToANewPeriod() throws Exception {
        mockMvc.perform(move(lessonId, adminToken, periods.get(2), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(periods.get(2)))
                .andExpect(jsonPath("$.hasConflict").value(false))
                .andExpect(jsonPath("$.conflicts.length()").value(0));

        assertThat(periodOf(lessonId)).isEqualTo(periods.get(2));
    }

    @Test
    void patch_movesToANewRoom() throws Exception {
        mockMvc.perform(move(lessonId, adminToken, null, roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId));
    }

    @Test
    void patch_reportsConflictsButStillCommitsTheMove() throws Exception {
        lesson(classBId, teacherAUserId, periods.get(1), null);

        mockMvc.perform(move(lessonId, adminToken, periods.get(1), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflict").value(true))
                .andExpect(jsonPath("$.conflicts[0].type").value("TEACHER_DOUBLE_BOOKED"));

        assertThat(periodOf(lessonId)).isEqualTo(periods.get(1));
    }

    @Test
    void patch_reportsRoomCapacityConflict() throws Exception {
        mockMvc.perform(move(lessonId, adminToken, null, smallRoomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflict").value(true))
                .andExpect(jsonPath("$.conflicts[0].type").value("ROOM_CAPACITY_EXCEEDED"));
    }

    @Test
    void patch_withNeitherField_returns400() throws Exception {
        mockMvc.perform(move(lessonId, adminToken, null, null)).andExpect(status().isBadRequest());
    }

    @Test
    void patch_unknownPeriodOrRoom_returns404() throws Exception {
        mockMvc.perform(move(lessonId, adminToken, 999_999_999L, null)).andExpect(status().isNotFound());
        mockMvc.perform(move(lessonId, adminToken, null, 999_999_999L)).andExpect(status().isNotFound());
    }

    @Test
    void patch_ownLesson_asTeacher_isAllowed() throws Exception {
        mockMvc.perform(move(lessonId, teacherAToken, periods.get(3), null)).andExpect(status().isOk());
    }

    @Test
    void patch_anotherTeachersLesson_returns403() throws Exception {
        mockMvc.perform(move(lessonId, teacherBToken, periods.get(3), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_crossTenant_returns404() throws Exception {
        mockMvc.perform(move(lessonId, otherTenantAdminToken, periods.get(3), null))
                .andExpect(status().isNotFound());
    }

    // ---------- SCHED-09 pin ----------

    @Test
    void pinAndUnpin_roundTrip() throws Exception {
        mockMvc.perform(post("/api/v1/lessons/" + lessonId + "/pin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPinned").value(true));
        assertThat(pinnedOf(lessonId)).isTrue();

        mockMvc.perform(delete("/api/v1/lessons/" + lessonId + "/pin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPinned").value(false));
        assertThat(pinnedOf(lessonId)).isFalse();
    }

    @Test
    void pin_ownLesson_asTeacher_isAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/lessons/" + lessonId + "/pin")
                        .header("Authorization", "Bearer " + teacherAToken))
                .andExpect(status().isOk());
    }

    @Test
    void pin_anotherTeachersLesson_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/lessons/" + lessonId + "/pin")
                        .header("Authorization", "Bearer " + teacherBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void pin_unknownLesson_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/lessons/999999999/pin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ---------- SCHED-10 swap ----------

    @Test
    void swap_exchangesPeriodsAndRooms() throws Exception {
        jdbcTemplate.update("UPDATE lessons SET room_id = ? WHERE id = ?", roomId, lessonId);
        long other = lesson(classBId, teacherBUserId, periods.get(1), null);

        mockMvc.perform(swap(lessonId, adminToken, other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        assertThat(periodOf(lessonId)).isEqualTo(periods.get(1));
        assertThat(periodOf(other)).isEqualTo(periods.get(0));
        assertThat(roomOf(other)).isEqualTo(roomId);
        assertThat(roomOf(lessonId)).isNull();
    }

    @Test
    void swap_thatWouldConflict_returns400AndChangesNothing() throws Exception {
        // Ann already teaches period 2 (a different class), so swapping her period-0 lesson into
        // period 2 would double-book her.
        lesson(classBId, teacherAUserId, periods.get(2), null);
        long benLesson = lesson(classAId, teacherBUserId, periods.get(2), null);

        mockMvc.perform(swap(lessonId, adminToken, benLesson)).andExpect(status().isBadRequest());

        assertThat(periodOf(lessonId)).isEqualTo(periods.get(0));
        assertThat(periodOf(benLesson)).isEqualTo(periods.get(2));
    }

    @Test
    void swap_ignoresTheCounterpartsOwnOldPosition() throws Exception {
        // Both lessons are for the same class, so each sees the other in the slot it is vacating.
        // That must not be reported as a clash, or no same-class swap would ever be possible.
        long other = lesson(classAId, teacherBUserId, periods.get(1), null);

        mockMvc.perform(swap(lessonId, adminToken, other)).andExpect(status().isOk());

        assertThat(periodOf(lessonId)).isEqualTo(periods.get(1));
        assertThat(periodOf(other)).isEqualTo(periods.get(0));
    }

    @Test
    void swap_withItself_returns400() throws Exception {
        mockMvc.perform(swap(lessonId, adminToken, lessonId)).andExpect(status().isBadRequest());
    }

    @Test
    void swap_unknownTarget_returns404() throws Exception {
        mockMvc.perform(swap(lessonId, adminToken, 999_999_999L)).andExpect(status().isNotFound());
    }

    @Test
    void swap_involvingAnotherTeachersLesson_returns403() throws Exception {
        long other = lesson(classBId, teacherBUserId, periods.get(1), null);

        mockMvc.perform(swap(lessonId, teacherAToken, other)).andExpect(status().isForbidden());
    }

    @Test
    void mutations_withoutToken_return401() throws Exception {
        mockMvc.perform(post("/api/v1/lessons/" + lessonId + "/pin")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.RequestBuilder move(
            long id, String token, Long periodId, Long room) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("periodId", periodId);
        payload.put("roomId", room);
        return patch("/api/v1/lessons/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }

    private org.springframework.test.web.servlet.RequestBuilder swap(
            long id, String token, long targetId) throws Exception {
        return post("/api/v1/lessons/" + id + "/swap")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetLessonId", targetId)));
    }

    private long lesson(long classId, long teacherUserId, long periodId, Long room) {
        long id = insertLesson(tenantId, timetableId, subjectId, classId, teacherUserId, periodId, MONDAY);
        if (room != null) {
            jdbcTemplate.update("UPDATE lessons SET room_id = ? WHERE id = ?", room, id);
        }
        return id;
    }

    private long createRoom(int capacity) throws Exception {
        return createdId(postCreated("/api/v1/rooms", adminToken, Map.of(
                "name", "Room " + UUID.randomUUID(), "type", "CLASSROOM", "capacity", capacity)));
    }

    private Long periodOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT schedule_period_id FROM lessons WHERE id = ?", Long.class, id);
    }

    private Long roomOf(long id) {
        return jdbcTemplate.queryForObject("SELECT room_id FROM lessons WHERE id = ?", Long.class, id);
    }

    private Boolean pinnedOf(long id) {
        return jdbcTemplate.queryForObject("SELECT is_pinned FROM lessons WHERE id = ?", Boolean.class, id);
    }
}
