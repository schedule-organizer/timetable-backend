package com.schediflow.dto.response;

import java.util.List;

public record HolidayImportResponse(
        int imported,
        int updated,
        int skipped,
        List<HolidayLessonConflictResponse> lessonConflicts
) {}
