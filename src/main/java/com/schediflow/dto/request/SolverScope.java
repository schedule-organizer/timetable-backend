package com.schediflow.dto.request;

import java.util.List;

/**
 * Which resources a partial regeneration may rearrange (SCHED-14). Any dimension may be supplied;
 * a lesson is in scope if it matches any of them. Out-of-scope lessons are frozen.
 */
public record SolverScope(
        List<Long> teacherIds,
        List<Long> classIds,
        List<Long> roomIds
) {

    public boolean isEmpty() {
        return isBlank(teacherIds) && isBlank(classIds) && isBlank(roomIds);
    }

    private static boolean isBlank(List<Long> ids) {
        return ids == null || ids.isEmpty();
    }
}
