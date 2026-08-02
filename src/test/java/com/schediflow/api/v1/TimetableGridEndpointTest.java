package com.schediflow.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimetableGridEndpointTest extends AbstractEndpointTest {

    /** A Monday, so dayOfWeek is 1. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    private String adminToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long timetableId;
    private long subjectId;
    private long classAId;
    private long classBId;
    private long roomId;
    private long teacherAId;
    private long teacherAUserId;
    private long teacherBUserId;
    private List<Long> periods;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@grid-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        String teacherEmail = "ta+" + UUID.randomUUID() + "@grid-test.edu";
        teacherAUserId = inviteAndGetUserId(adminToken, teacherEmail);
        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);
        teacherBUserId = inviteAndGetUserId(adminToken, "tb+" + UUID.randomUUID() + "@grid-test.edu");

        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-grid.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        classAId = createClass(adminToken, "8A");
        classBId = createClass(adminToken, "8B");
        roomId = createRoom("Lab 1", 30);
        teacherAId = createTeacherProfile(adminToken, teacherAUserId, "Ann Teacher");
        createTeacherProfile(adminToken, teacherBUserId, "Ben Teacher");
        periods = periodIds(adminToken);

        long termId = createTerm(adminToken, MONDAY.minusMonths(1), MONDAY.plusMonths(1));
        timetableId = createdId(postCreated("/api/v1/timetables", adminToken, Map.of(
                "name", "Grid " + UUID.randomUUID(), "termId", termId)));
    }

    @Test
    void get_returnsGridRowsWithJoinedNames() throws Exception {
        long lessonId = lesson(classAId, teacherAUserId, periods.get(0), roomId);

        mockMvc.perform(get(url()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lessonId").value(lessonId))
                .andExpect(jsonPath("$[0].subjectName").value("Maths"))
                .andExpect(jsonPath("$[0].teacherName").value("Ann Teacher"))
                .andExpect(jsonPath("$[0].roomName").value("Lab 1"))
                .andExpect(jsonPath("$[0].periodId").value(periods.get(0)))
                .andExpect(jsonPath("$[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$[0].isPinned").value(false))
                .andExpect(jsonPath("$[0].hasConflict").value(false));
    }

    @Test
    void get_unroomedLesson_hasNullRoomName() throws Exception {
        lesson(classAId, teacherAUserId, periods.get(0), null);

        mockMvc.perform(get(url()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].roomName").doesNotExist());
    }

    @Test
    void get_flagsTeacherDoubleBooking() throws Exception {
        lesson(classAId, teacherAUserId, periods.get(0), null);
        lesson(classBId, teacherAUserId, periods.get(0), null);

        mockMvc.perform(get(url()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].hasConflict").value(true))
                .andExpect(jsonPath("$[1].hasConflict").value(true));
    }

    @Test
    void get_doesNotFlagLessonsInDifferentPeriods() throws Exception {
        lesson(classAId, teacherAUserId, periods.get(0), null);
        lesson(classBId, teacherAUserId, periods.get(1), null);

        mockMvc.perform(get(url()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasConflict").value(false))
                .andExpect(jsonPath("$[1].hasConflict").value(false));
    }

    @Test
    void get_flagsRoomTooSmallForTheClass() throws Exception {
        long tinyRoom = createRoom("Broom cupboard", 5);
        lesson(classAId, teacherAUserId, periods.get(0), tinyRoom);

        mockMvc.perform(get(url()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasConflict").value(true));
    }

    @Test
    void get_reflectsPinnedFlag() throws Exception {
        long lessonId = lesson(classAId, teacherAUserId, periods.get(0), null);
        jdbcTemplate.update("UPDATE lessons SET is_pinned = TRUE WHERE id = ?", lessonId);

        mockMvc.perform(get(url()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isPinned").value(true));
    }

    @Test
    void get_filtersByTeacher() throws Exception {
        lesson(classAId, teacherAUserId, periods.get(0), null);
        lesson(classBId, teacherBUserId, periods.get(1), null);

        mockMvc.perform(get(url())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("teacherId", String.valueOf(teacherAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("Ann Teacher"));
    }

    @Test
    void get_filtersByClassAndRoom() throws Exception {
        lesson(classAId, teacherAUserId, periods.get(0), roomId);
        lesson(classBId, teacherBUserId, periods.get(1), null);

        mockMvc.perform(get(url())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("classId", String.valueOf(classAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get(url())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("roomId", String.valueOf(roomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void get_unknownTeacherFilter_returnsEmptyRatherThanEverything() throws Exception {
        lesson(classAId, teacherAUserId, periods.get(0), null);

        mockMvc.perform(get(url())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("teacherId", "999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void get_emptyTimetable_returnsEmptyList() throws Exception {
        mockMvc.perform(get(url()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void get_asTeacher_isAllowed() throws Exception {
        lesson(classAId, teacherAUserId, periods.get(0), null);

        mockMvc.perform(get(url()).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void get_unknownTimetable_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/timetables/999999999/lessons")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_crossTenant_returns404() throws Exception {
        mockMvc.perform(get(url()).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(url())).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String url() {
        return "/api/v1/timetables/" + timetableId + "/lessons";
    }

    private long lesson(long classId, long teacherUserId, long periodId, Long room) {
        long id = insertLesson(tenantId, timetableId, subjectId, classId, teacherUserId, periodId, MONDAY);
        if (room != null) {
            jdbcTemplate.update("UPDATE lessons SET room_id = ? WHERE id = ?", room, id);
        }
        return id;
    }

    private long createRoom(String name, int capacity) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name + " " + UUID.randomUUID());
        payload.put("type", "CLASSROOM");
        payload.put("capacity", capacity);
        long id = createdId(postCreated("/api/v1/rooms", adminToken, payload));
        jdbcTemplate.update("UPDATE rooms SET name = ? WHERE id = ?", name, id);
        return id;
    }
}
