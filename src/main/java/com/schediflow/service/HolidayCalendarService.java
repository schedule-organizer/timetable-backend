package com.schediflow.service;

import com.schediflow.domain.HolidayCalendar;
import com.schediflow.dto.request.HolidayCalendarRequest;
import com.schediflow.dto.response.HolidayCalendarResponse;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.AcademicYearRepository;
import com.schediflow.repository.HolidayCalendarRepository;
import com.schediflow.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

@Service
public class HolidayCalendarService {

    private final HolidayCalendarRepository holidayCalendarRepository;
    private final AcademicYearRepository academicYearRepository;

    public HolidayCalendarService(HolidayCalendarRepository holidayCalendarRepository,
                                   AcademicYearRepository academicYearRepository) {
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.academicYearRepository = academicYearRepository;
    }

    public List<HolidayCalendarResponse> list() {
        return holidayCalendarRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public HolidayCalendarResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public HolidayCalendarResponse create(Long tenantId, HolidayCalendarRequest req) {
        validateAcademicYearExists(tenantId, req.academicYearId());
        assertCalendarUnique(tenantId, req.academicYearId(), null);

        HolidayCalendar entity = new HolidayCalendar();
        entity.setTenantId(tenantId);
        entity.setAcademicYearId(req.academicYearId());
        entity.setName(req.name());
        entity.setCountry(req.country());
        entity.setRegion(req.region());

        return saveAndMap(entity);
    }

    @Transactional
    public HolidayCalendarResponse update(Long tenantId, Long id, HolidayCalendarRequest req) {
        HolidayCalendar entity = findOrThrow(id);
        validateAcademicYearExists(tenantId, req.academicYearId());
        assertCalendarUnique(tenantId, req.academicYearId(), id);

        entity.setAcademicYearId(req.academicYearId());
        entity.setName(req.name());
        entity.setCountry(req.country());
        entity.setRegion(req.region());

        return saveAndMap(entity);
    }

    @Transactional
    public void delete(Long id) {
        HolidayCalendar entity = findOrThrow(id);
        holidayCalendarRepository.delete(entity);
    }

    private HolidayCalendar findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return holidayCalendarRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday calendar not found: " + id));
    }

    private void validateAcademicYearExists(Long tenantId, Long academicYearId) {
        academicYearRepository.findByIdAndTenantId(academicYearId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found: " + academicYearId));
    }

    private void assertCalendarUnique(Long tenantId, Long academicYearId, Long excludeId) {
        boolean taken = excludeId == null
                ? holidayCalendarRepository.existsByAcademicYearIdAndTenantId(academicYearId, tenantId)
                : holidayCalendarRepository.existsByAcademicYearIdAndTenantIdAndIdNot(academicYearId, tenantId, excludeId);
        if (taken) {
            throw new ConflictException("Holiday calendar already exists for this academic year");
        }
    }

    private HolidayCalendarResponse saveAndMap(HolidayCalendar entity) {
        try {
            return toResponse(holidayCalendarRepository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueConstraintViolation(ex)) {
                throw new ConflictException("Holiday calendar already exists for this academic year");
            }
            throw ex;
        }
    }

    /**
     * Concurrent requests can pass {@link #assertCalendarUnique} and hit the UNIQUE(tenant_id, academic_year_id)
     * index. PostgreSQL/H2 report SQLState 23505 for unique violations.
     */
    private static boolean isUniqueConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            return true;
        }
        String msg = ex.getMessage();
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("unique") && (m.contains("academic_year") || m.contains("holiday_calendar"));
    }

    private HolidayCalendarResponse toResponse(HolidayCalendar entity) {
        return new HolidayCalendarResponse(
                entity.getId(),
                entity.getAcademicYearId(),
                entity.getName(),
                entity.getCountry(),
                entity.getRegion(),
                entity.getCreatedAt());
    }
}
