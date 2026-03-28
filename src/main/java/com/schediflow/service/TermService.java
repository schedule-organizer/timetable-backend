package com.schediflow.service;

import com.schediflow.domain.AcademicYear;
import com.schediflow.domain.Term;
import com.schediflow.dto.request.TermRequest;
import com.schediflow.dto.response.TermResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.AcademicYearRepository;
import com.schediflow.repository.TermRepository;
import com.schediflow.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Service
public class TermService {

    private final TermRepository termRepository;
    private final AcademicYearRepository academicYearRepository;

    public TermService(TermRepository termRepository, AcademicYearRepository academicYearRepository) {
        this.termRepository = termRepository;
        this.academicYearRepository = academicYearRepository;
    }

    public List<TermResponse> list(Long tenantId, Long academicYearId) {
        loadAcademicYearOrThrow(tenantId, academicYearId);
        return termRepository
                .findByAcademicYearIdAndTenantIdOrderByOrdinalAsc(academicYearId, tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TermResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public TermResponse create(Long tenantId, TermRequest req) {
        AcademicYear year = loadAcademicYearOrThrow(tenantId, req.academicYearId());
        validateTermDateOrder(req);
        validateTermDatesWithinYear(year, req.startDate(), req.endDate());
        assertOrdinalAvailable(tenantId, req.academicYearId(), req.ordinal(), null);

        Term entity = new Term();
        entity.setTenantId(tenantId);
        entity.setAcademicYearId(req.academicYearId());
        mapFields(entity, req);
        return saveAndMap(entity);
    }

    @Transactional
    public TermResponse update(Long tenantId, Long id, TermRequest req) {
        Term entity = findOrThrow(id);
        AcademicYear year = loadAcademicYearOrThrow(tenantId, req.academicYearId());
        validateTermDateOrder(req);
        validateTermDatesWithinYear(year, req.startDate(), req.endDate());
        assertOrdinalAvailable(tenantId, req.academicYearId(), req.ordinal(), id);

        entity.setAcademicYearId(req.academicYearId());
        mapFields(entity, req);
        return saveAndMap(entity);
    }

    @Transactional
    public void delete(Long id) {
        Term entity = findOrThrow(id);
        termRepository.delete(entity);
    }

    private Term findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return termRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Term not found: " + id));
    }

    private AcademicYear loadAcademicYearOrThrow(Long tenantId, Long academicYearId) {
        return academicYearRepository
                .findByIdAndTenantId(academicYearId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found: " + academicYearId));
    }

    private void validateTermDateOrder(TermRequest req) {
        if (!req.startDate().isBefore(req.endDate())) {
            throw new BadRequestException("startDate must be before endDate");
        }
    }

    private void validateTermDatesWithinYear(AcademicYear year, LocalDate start, LocalDate end) {
        if (start.isBefore(year.getStartDate()) || end.isAfter(year.getEndDate())) {
            throw new BadRequestException("Term dates must fall within the academic year");
        }
    }

    private void assertOrdinalAvailable(Long tenantId, Long academicYearId, Integer ordinal, Long excludeTermId) {
        boolean taken =
                excludeTermId == null
                        ? termRepository.existsByAcademicYearIdAndTenantIdAndOrdinal(
                                academicYearId, tenantId, ordinal)
                        : termRepository.existsByAcademicYearIdAndTenantIdAndOrdinalAndIdNot(
                                academicYearId, tenantId, ordinal, excludeTermId);
        if (taken) {
            throw new ConflictException("Ordinal already used for this academic year");
        }
    }

    private void mapFields(Term entity, TermRequest req) {
        entity.setName(req.name());
        entity.setOrdinal(req.ordinal());
        entity.setStartDate(req.startDate());
        entity.setEndDate(req.endDate());
    }

    private TermResponse saveAndMap(Term entity) {
        try {
            return toResponse(termRepository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueConstraintRaceOnOrdinal(ex)) {
                throw new ConflictException("Ordinal already used for this academic year");
            }
            throw ex;
        }
    }

    /**
     * Concurrent requests can pass {@link #assertOrdinalAvailable} and hit the DB unique index on
     * (academic_year_id, ordinal). PostgreSQL/H2 report SQLState 23505 for unique violations.
     */
    private static boolean isUniqueConstraintRaceOnOrdinal(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            return true;
        }
        String msg = ex.getMessage();
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("unique") && (m.contains("ordinal") || m.contains("academic_year"));
    }

    private TermResponse toResponse(Term entity) {
        return new TermResponse(
                entity.getId(),
                entity.getAcademicYearId(),
                entity.getName(),
                entity.getOrdinal(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getCreatedAt());
    }
}
