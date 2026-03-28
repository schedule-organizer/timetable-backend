package com.schediflow.service;

import com.schediflow.domain.AcademicYear;
import com.schediflow.dto.request.AcademicYearRequest;
import com.schediflow.dto.response.AcademicYearResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.AcademicYearRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AcademicYearService {

    private final AcademicYearRepository repository;

    public AcademicYearService(AcademicYearRepository repository) {
        this.repository = repository;
    }

    public List<AcademicYearResponse> list() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public AcademicYearResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public AcademicYearResponse create(Long tenantId, AcademicYearRequest req) {
        validateDates(req);
        if (req.isActive()) {
            deactivateCurrentActive(tenantId);
        }
        AcademicYear entity = new AcademicYear();
        entity.setTenantId(tenantId);
        mapFields(entity, req);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public AcademicYearResponse update(Long tenantId, Long id, AcademicYearRequest req) {
        AcademicYear entity = findOrThrow(id);
        validateDates(req);
        if (req.isActive() && !entity.isActive()) {
            deactivateCurrentActive(tenantId);
        }
        mapFields(entity, req);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        AcademicYear entity = findOrThrow(id);
        repository.delete(entity);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AcademicYear findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found: " + id));
    }

    private void validateDates(AcademicYearRequest req) {
        if (!req.startDate().isBefore(req.endDate())) {
            throw new BadRequestException("startDate must be before endDate");
        }
    }

    private void deactivateCurrentActive(Long tenantId) {
        repository.findByTenantIdAndActiveTrue(tenantId)
                .forEach(y -> {
                    y.setActive(false);
                    repository.save(y);
                });
    }

    private void mapFields(AcademicYear entity, AcademicYearRequest req) {
        entity.setName(req.name());
        entity.setStartDate(req.startDate());
        entity.setEndDate(req.endDate());
        entity.setActive(req.isActive());
    }

    private AcademicYearResponse toResponse(AcademicYear entity) {
        return new AcademicYearResponse(
                entity.getId(),
                entity.getName(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }
}
