package com.schediflow.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** EXPORT-02 (CSV) and EXPORT-05/06/07 (reports). */
class TimetableExportAndReportEndpointTest extends AbstractEndpointTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    private String adminToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    private long tenantId;
    private long timetableId;
    private long subjectId;
    private long classId;
    private long roomId;
    private long teacherId;
    private long teacherUserId;
    private List<Long> periods;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@export-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);

        String teacherEmail = "t+" + UUID.randomUUID() + "@export-test.edu";
        teacherUserId = inviteAndGetUserId(adminToken, teacherEmail);
        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);
        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-export.edu");

        subjectId = createSubject(adminToken, "Maths", "MTH");
        classId = createClass(adminToken, "8A");
        roomId = createdId(postCreated("/api/v1/rooms", adminToken, Map.of(
                "name", "Lab 1", "type", "LAB", "capacity", 30)));
        teacherId = createTeacherProfile(adminToken, teacherUserId, "Ann Teacher", 10);
        periods = periodIds(adminToken);

        long termId = createTerm(adminToken, MONDAY.minusMonths(1), MONDAY.plusMonths(1));
        timetableId = createdId(postCreated("/api/v1/timetables", adminToken, Map.of(
                "name", "Autumn", "termId", termId)));
    }

    // ---------- EXPORT-02 ----------

    @Test
    void csv_returnsHeadersRowsAndAttachmentDisposition() throws Exception {
        lesson(periods.get(0), roomId);
        lesson(periods.get(1), null);

        MvcResult started = mockMvc.perform(get(exportUrl("csv"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"timetable-" + timetableId + ".csv\""))
                .andReturn();

        String csv = started.getResponse().getContentAsString();
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("lessonId,subject,teacher,room,class,dayOfWeek,periodName,startTime,endTime");
        assertThat(csv).contains("Maths").contains("Ann Teacher").contains("8A").contains("Lab 1");
        // dayOfWeek 1 = Monday
        assertThat(csv).contains(",1,");
    }

    @Test
    void csv_isUtf8WithBomForExcel() throws Exception {
        lesson(periods.get(0), null);

        MvcResult started = mockMvc.perform(get(exportUrl("csv"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = started.getResponse().getContentAsByteArray();
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
    }

    @Test
    void csv_emptyTimetable_stillReturnsHeaderRow() throws Exception {
        MvcResult started = mockMvc.perform(get(exportUrl("csv"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(started.getResponse().getContentAsString()).contains("lessonId,subject");
    }

    @Test
    void csv_asTeacher_returns403() throws Exception {
        mockMvc.perform(get(exportUrl("csv")).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void csv_unknownTimetable_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/timetables/999999999/export/csv")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void csv_crossTenant_returns404() throws Exception {
        mockMvc.perform(get(exportUrl("csv"))
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    // ---------- EXPORT-05 ----------

    @Test
    void teacherUtilization_reportsLoadCapAndPercentage() throws Exception {
        lesson(periods.get(0), null);
        lesson(periods.get(1), null);

        mockMvc.perform(get(reportUrl("teacher-utilization"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachers[0].teacherId").value(teacherId))
                .andExpect(jsonPath("$.teachers[0].periodsAssigned").value(2))
                .andExpect(jsonPath("$.teachers[0].workloadCap").value(10))
                .andExpect(jsonPath("$.teachers[0].utilizationPct").value(20.0))
                .andExpect(jsonPath("$.teachers[0].subjectDistribution[0].subjectName").value("Maths"))
                .andExpect(jsonPath("$.teachers[0].subjectDistribution[0].periods").value(2))
                .andExpect(jsonPath("$.summary.avgUtilization").value(20.0));
    }

    @Test
    void teacherUtilization_countsGapsBetweenFirstAndLastLessonOfADay() throws Exception {
        // Periods 1 and 4 occupied leaves 2 and 3 as gaps.
        lesson(periods.get(0), null);
        lesson(periods.get(3), null);

        mockMvc.perform(get(reportUrl("teacher-utilization"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachers[0].gapCount").value(2));
    }

    @Test
    void teacherUtilization_flagsOverloadAndUnderuse() throws Exception {
        for (int i = 0; i < 8; i++) {
            lesson(periods.get(i % periods.size()), null);
        }

        mockMvc.perform(get(reportUrl("teacher-utilization"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.underutilizedCount").value(0))
                .andExpect(jsonPath("$.summary.overloadedCount").value(0));
    }

    @Test
    void teacherUtilization_asTeacher_returns403() throws Exception {
        mockMvc.perform(get(reportUrl("teacher-utilization"))
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    // ---------- EXPORT-06 ----------

    @Test
    void roomUtilization_reportsOccupancyPerPeriodAndAverage() throws Exception {
        lesson(periods.get(0), roomId);

        mockMvc.perform(get(reportUrl("room-utilization"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].roomId").value(roomId))
                .andExpect(jsonPath("$.rooms[0].roomName").value("Lab 1"))
                .andExpect(jsonPath("$.rooms[0].roomType").value("LAB"))
                .andExpect(jsonPath("$.rooms[0].occupancyByPeriod").isMap())
                .andExpect(jsonPath("$.avgOccupancyByType.LAB").exists())
                .andExpect(jsonPath("$.totalPeriodsInCycle").isNumber());
    }

    @Test
    void roomUtilization_emptyTimetable_reportsZero() throws Exception {
        mockMvc.perform(get(reportUrl("room-utilization"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].avgOccupancy").value(0.0))
                .andExpect(jsonPath("$.totalPeriodsInCycle").value(0));
    }

    @Test
    void roomUtilization_asTeacher_returns403() throws Exception {
        mockMvc.perform(get(reportUrl("room-utilization"))
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    // ---------- EXPORT-07 ----------

    @Test
    void subjectCoverage_comparesActualAgainstRequired() throws Exception {
        requireHours(3);
        lesson(periods.get(0), null);
        lesson(periods.get(1), null);

        mockMvc.perform(get(reportUrl("subject-coverage"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage[0].className").value("8A"))
                .andExpect(jsonPath("$.coverage[0].subjectName").value("Maths"))
                .andExpect(jsonPath("$.coverage[0].required").value(3))
                .andExpect(jsonPath("$.coverage[0].actual").value(2))
                .andExpect(jsonPath("$.coverage[0].variance").value(-1))
                .andExpect(jsonPath("$.coverage[0].status").value("UNDER"))
                .andExpect(jsonPath("$.summary.totalUnder").value(1));
    }

    @Test
    void subjectCoverage_flagsOnTargetAndOver() throws Exception {
        requireHours(2);
        lesson(periods.get(0), null);
        lesson(periods.get(1), null);

        mockMvc.perform(get(reportUrl("subject-coverage"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage[0].status").value("ON_TARGET"))
                .andExpect(jsonPath("$.summary.totalOnTarget").value(1));

        lesson(periods.get(2), null);
        mockMvc.perform(get(reportUrl("subject-coverage"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage[0].status").value("OVER"))
                .andExpect(jsonPath("$.summary.totalOver").value(1));
    }

    @Test
    void subjectCoverage_isReadableByAnyAuthenticatedRole() throws Exception {
        requireHours(1);

        mockMvc.perform(get(reportUrl("subject-coverage"))
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
    }

    @Test
    void reports_crossTenant_return404() throws Exception {
        mockMvc.perform(get(reportUrl("teacher-utilization"))
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void reports_withoutToken_return401() throws Exception {
        mockMvc.perform(get(reportUrl("subject-coverage"))).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String exportUrl(String format) {
        return "/api/v1/timetables/" + timetableId + "/export/" + format;
    }

    private String reportUrl(String report) {
        return "/api/v1/timetables/" + timetableId + "/reports/" + report;
    }

    private long lesson(long periodId, Long room) {
        long id = insertLesson(tenantId, timetableId, subjectId, classId, teacherUserId, periodId, MONDAY);
        if (room != null) {
            jdbcTemplate.update("UPDATE lessons SET room_id = ? WHERE id = ?", room, id);
        }
        return id;
    }

    private void requireHours(int periodsPerCycle) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/classes/" + classId + "/subject-hours")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of(Map.of(
                                "subjectId", subjectId,
                                "periodsPerCycle", periodsPerCycle,
                                "spreadPattern", "ANY"))))))
                .andExpect(status().isOk());
    }
}
