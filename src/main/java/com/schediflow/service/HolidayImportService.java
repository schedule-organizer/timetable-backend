package com.schediflow.service;

import com.schediflow.domain.HolidayDate;
import com.schediflow.domain.HolidaySource;
import com.schediflow.domain.HolidayType;
import com.schediflow.dto.request.HolidayImportRequest;
import com.schediflow.dto.response.HolidayLessonConflictResponse;
import com.schediflow.dto.response.HolidayImportResponse;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.integration.holiday.HolidayFeedClient;
import com.schediflow.integration.holiday.HolidayFeedItem;
import com.schediflow.repository.HolidayCalendarRepository;
import com.schediflow.repository.HolidayDateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class HolidayImportService {

    private static final int MAX_NAME_LENGTH = 100;

    private final HolidayFeedClient holidayFeedClient;
    private final HolidayCalendarRepository holidayCalendarRepository;
    private final HolidayDateRepository holidayDateRepository;
    private final ConflictDetectionService conflictDetectionService;

    public HolidayImportService(
            HolidayFeedClient holidayFeedClient,
            HolidayCalendarRepository holidayCalendarRepository,
            HolidayDateRepository holidayDateRepository,
            ConflictDetectionService conflictDetectionService) {
        this.holidayFeedClient = holidayFeedClient;
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.holidayDateRepository = holidayDateRepository;
        this.conflictDetectionService = conflictDetectionService;
    }

    @Transactional
    public HolidayImportResponse importPublicHolidays(Long tenantId, HolidayImportRequest request) {
        var calendar = holidayCalendarRepository.findByIdAndTenantId(request.calendarId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday calendar not found: " + request.calendarId()));
        Long academicYearId = calendar.getAcademicYearId();

        String country = request.country().toUpperCase(Locale.ROOT);
        List<HolidayFeedItem> items = holidayFeedClient.fetchPublicHolidays(
                country,
                request.year(),
                request.region());

        int imported = 0;
        int updated = 0;
        int skipped = 0;
        Set<LocalDate> newlyAddedDates = new HashSet<>();

        for (HolidayFeedItem item : items) {
            String name = truncateName(item.name());
            var existing = holidayDateRepository.findByHolidayCalendarIdAndTenantIdAndDate(
                    request.calendarId(), tenantId, item.date());

            if (existing.isEmpty()) {
                try {
                    HolidayDate row = new HolidayDate();
                    row.setHolidayCalendarId(request.calendarId());
                    row.setTenantId(tenantId);
                    row.setName(name);
                    row.setDate(item.date());
                    row.setType(HolidayType.PUBLIC_HOLIDAY);
                    row.setSource(HolidaySource.IMPORTED);
                    holidayDateRepository.save(row);
                    imported++;
                    newlyAddedDates.add(item.date());
                } catch (DataIntegrityViolationException ignored) {
                    // Concurrent import inserted the same date; treat as skip.
                    skipped++;
                }
            } else {
                HolidayDate row = existing.get();
                if (HolidayType.PUBLIC_HOLIDAY.equals(row.getType()) && name.equals(row.getName())) {
                    skipped++;
                } else {
                    row.setName(name);
                    row.setType(HolidayType.PUBLIC_HOLIDAY);
                    holidayDateRepository.save(row);
                    updated++;
                }
            }
        }

        List<HolidayLessonConflictResponse> lessonConflicts = new ArrayList<>();
        for (LocalDate d : newlyAddedDates) {
            lessonConflicts.addAll(
                    conflictDetectionService.findPublishedLessonHolidayConflicts(tenantId, academicYearId, d));
        }

        return new HolidayImportResponse(imported, updated, skipped, List.copyOf(lessonConflicts));
    }

    private static String truncateName(String name) {
        if (name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH));
    }
}
