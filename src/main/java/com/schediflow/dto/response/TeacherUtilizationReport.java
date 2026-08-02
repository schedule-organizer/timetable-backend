package com.schediflow.dto.response;

import java.util.List;

/** EXPORT-05: how heavily each teacher is loaded in one timetable. */
public record TeacherUtilizationReport(
        List<TeacherUtilizationRow> teachers,
        Summary summary
) {

    /**
     * @param utilizationPct periodsAssigned / workloadCap × 100, or null when the teacher is uncapped
     * @param gapCount       free periods between a teacher's first and last lesson each day
     */
    public record TeacherUtilizationRow(
            Long teacherId,
            String displayName,
            int periodsAssigned,
            Integer workloadCap,
            Double utilizationPct,
            int gapCount,
            List<SubjectCount> subjectDistribution) {}

    public record SubjectCount(String subjectName, int periods) {}

    public record Summary(
            Double avgUtilization,
            int overloadedCount,
            int underutilizedCount) {}
}
