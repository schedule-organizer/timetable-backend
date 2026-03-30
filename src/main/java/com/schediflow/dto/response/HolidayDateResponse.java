package com.schediflow.dto.response;

import com.schediflow.domain.HolidayType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record HolidayDateResponse(
        Long id,
        Long calendarId,
        LocalDate date,
        String name,
        HolidayType type,
        OffsetDateTime createdAt
) {}
