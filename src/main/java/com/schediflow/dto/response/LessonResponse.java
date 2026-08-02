package com.schediflow.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record LessonResponse(
        Long id,
        Long timetableId,
        Long subjectId,
        Long classId,
        Long teacherUserId,
        Long roomId,
        Long periodId,
        LocalDate scheduledDate,
        @JsonProperty("isPinned") boolean isPinned,
        boolean hasConflict,
        List<ConflictViolation> conflicts
) {}
