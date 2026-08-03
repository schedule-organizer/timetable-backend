package com.schediflow.dto.response;

import java.util.List;

/** EXPORT-07: scheduled periods per class × subject against what RES-06 requires. */
public record SubjectCoverageReport(
        List<CoverageRow> coverage,
        Summary summary
) {

    /** @param variance actual − required; negative means under-scheduled */
    public record CoverageRow(
            Long classId,
            String className,
            Long subjectId,
            String subjectName,
            int required,
            int actual,
            int variance,
            String status) {}

    public record Summary(int totalUnder, int totalOver, int totalOnTarget) {}
}
