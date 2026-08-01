package com.schediflow.dto.response;

import java.util.List;

/**
 * A teacher who could take a given lesson.
 *
 * @param qualifications  subject ids the teacher is qualified to teach
 * @param currentWorkload lessons already taught or covered by this teacher in the lesson's timetable
 * @param workloadCap     the teacher's cap, or {@code null} when uncapped
 * @param workloadGap     {@code workloadCap - currentWorkload}, or {@code null} when uncapped
 */
public record CoverCandidateResponse(
        Long teacherId,
        String displayName,
        List<Long> qualifications,
        int currentWorkload,
        Integer workloadCap,
        Integer workloadGap
) {}
