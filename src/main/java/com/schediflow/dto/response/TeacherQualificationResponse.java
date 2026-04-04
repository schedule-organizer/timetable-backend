package com.schediflow.dto.response;

import java.time.OffsetDateTime;

public record TeacherQualificationResponse(
        Long id,
        Long teacherId,
        Long subjectId,
        Integer periodsPerCycle,
        OffsetDateTime createdAt
) {}
