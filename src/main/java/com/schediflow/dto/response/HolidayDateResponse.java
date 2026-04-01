package com.schediflow.dto.response;

import com.schediflow.domain.HolidaySource;
import com.schediflow.domain.HolidayType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record HolidayDateResponse(
        Long id,
        Long calendarId,
        LocalDate date,
        String name,
        HolidayType type,
        HolidaySource source,
        OffsetDateTime createdAt,
        List<HolidayLessonConflictResponse> lessonConflicts
) {}
