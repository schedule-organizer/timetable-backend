package com.schediflow.service;

import com.schediflow.domain.HolidayDate;
import com.schediflow.domain.HolidaySource;
import com.schediflow.dto.request.HolidayDateRequest;
import com.schediflow.dto.request.HolidayDateUpdateRequest;
import com.schediflow.dto.response.HolidayDateResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.HolidayCalendarRepository;
import com.schediflow.repository.HolidayDateRepository;
import com.schediflow.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Service
public class HolidayDateService {

    private final HolidayDateRepository holidayDateRepository;
    private final HolidayCalendarRepository holidayCalendarRepository;

    public HolidayDateService(HolidayDateRepository holidayDateRepository,
                               HolidayCalendarRepository holidayCalendarRepository) {
        this.holidayDateRepository = holidayDateRepository;
        this.holidayCalendarRepository = holidayCalendarRepository;
    }

    public List<HolidayDateResponse> listByAcademicYear(Long tenantId, Long academicYearId) {
        var calendar = holidayCalendarRepository.findByAcademicYearIdAndTenantId(academicYearId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found in tenant: " + academicYearId));
        return holidayDateRepository.findByHolidayCalendarIdOrderByDateAsc(calendar.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HolidayDateResponse addDate(Long calendarId, HolidayDateRequest req) {
        Long tenantId = TenantContext.getTenantId();
        assertCalendarExists(calendarId, tenantId);
        assertDateNotDuplicate(calendarId, tenantId, req);

        HolidayDate entity = new HolidayDate();
        entity.setHolidayCalendarId(calendarId);
        entity.setTenantId(tenantId);
        entity.setDate(req.date());
        entity.setName(req.name());
        entity.setType(req.type());
        entity.setSource(HolidaySource.MANUAL);

        return saveNewDate(entity, req.date());
    }

    @Transactional
    public HolidayDateResponse updateDate(Long calendarId, Long dateId, HolidayDateUpdateRequest req) {
        Long tenantId = TenantContext.getTenantId();
        assertCalendarExists(calendarId, tenantId);
        HolidayDate entity = findDateOrThrow(dateId, calendarId, tenantId);

        entity.setName(req.name());
        entity.setType(req.type());

        return toResponse(holidayDateRepository.save(entity));
    }

    @Transactional
    public void deleteDate(Long calendarId, Long dateId) {
        Long tenantId = TenantContext.getTenantId();
        assertCalendarExists(calendarId, tenantId);
        HolidayDate entity = findDateOrThrow(dateId, calendarId, tenantId);
        holidayDateRepository.delete(entity);
    }

    private void assertCalendarExists(Long calendarId, Long tenantId) {
        holidayCalendarRepository.findByIdAndTenantId(calendarId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday calendar not found: " + calendarId));
    }

    private void assertDateNotDuplicate(Long calendarId, Long tenantId, HolidayDateRequest req) {
        if (holidayDateRepository.existsByHolidayCalendarIdAndTenantIdAndDate(calendarId, tenantId, req.date())) {
            throw duplicateDateBadRequest(req.date());
        }
    }

    private HolidayDateResponse saveNewDate(HolidayDate entity, LocalDate date) {
        try {
            return toResponse(holidayDateRepository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            if (isHolidayDateUniqueConstraintViolation(ex)) {
                throw duplicateDateBadRequest(date);
            }
            throw ex;
        }
    }

    /**
     * Concurrent requests can pass {@link #assertDateNotDuplicate} and hit
     * UNIQUE(holiday_calendar_id, date) (V009). PostgreSQL/H2 use SQLState 23505.
     */
    private static boolean isHolidayDateUniqueConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            return true;
        }
        String msg = ex.getMessage();
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("unique") && (m.contains("holiday_dates") || m.contains("uq_holiday_dates"));
    }

    private static BadRequestException duplicateDateBadRequest(LocalDate date) {
        return new BadRequestException("A holiday date already exists for " + date + " in this calendar");
    }

    private HolidayDate findDateOrThrow(Long dateId, Long calendarId, Long tenantId) {
        return holidayDateRepository.findByIdAndHolidayCalendarIdAndTenantId(dateId, calendarId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday date not found: " + dateId));
    }

    private HolidayDateResponse toResponse(HolidayDate entity) {
        return new HolidayDateResponse(
                entity.getId(),
                entity.getHolidayCalendarId(),
                entity.getDate(),
                entity.getName(),
                entity.getType(),
                entity.getSource(),
                entity.getCreatedAt());
    }
}
