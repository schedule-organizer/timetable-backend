package com.schediflow.api.v1;

import com.schediflow.dto.response.RoomUtilizationReport;
import com.schediflow.dto.response.SubjectCoverageReport;
import com.schediflow.dto.response.TeacherUtilizationReport;
import com.schediflow.service.TimetableReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only reports over a timetable (EXPORT-05/06/07). */
@RestController
@RequestMapping("/api/v1/timetables/{timetableId}/reports")
public class TimetableReportController {

    private final TimetableReportService reportService;

    public TimetableReportController(TimetableReportService reportService) {
        this.reportService = reportService;
    }

    /** @return 200 with per-teacher load and a summary; 403 without ADMIN/MOD; 404 if not in tenant */
    @GetMapping("/teacher-utilization")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TeacherUtilizationReport> teacherUtilization(@PathVariable Long timetableId) {
        return ResponseEntity.ok(reportService.teacherUtilization(timetableId));
    }

    /** @return 200 with per-room occupancy; 403 without ADMIN/MOD; 404 if not in tenant */
    @GetMapping("/room-utilization")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<RoomUtilizationReport> roomUtilization(@PathVariable Long timetableId) {
        return ResponseEntity.ok(reportService.roomUtilization(timetableId));
    }

    /** Readable by all authenticated roles, per the story. */
    @GetMapping("/subject-coverage")
    public ResponseEntity<SubjectCoverageReport> subjectCoverage(@PathVariable Long timetableId) {
        return ResponseEntity.ok(reportService.subjectCoverage(timetableId));
    }
}
