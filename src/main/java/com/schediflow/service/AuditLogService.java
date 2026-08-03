package com.schediflow.service;

import com.schediflow.domain.AuditLogEntry;
import com.schediflow.dto.response.AuditLogResponse;
import com.schediflow.repository.AuditLogRepository;
import com.schediflow.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Reads the audit trail (EXPORT-08). Writing is done by {@code AuditAspect}. */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * @param startDate inclusive from the start of that day
     * @param endDate   inclusive to the end of that day, so a single-day filter returns that day
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(
            Long actorId, String entityType, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        return auditLogRepository
                .search(
                        tenantId,
                        actorId,
                        (entityType == null || entityType.isBlank()) ? null : entityType.trim(),
                        startDate == null ? null : startDate.atStartOfDay().atOffset(ZoneOffset.UTC),
                        endDate == null ? null : endDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC),
                        pageable)
                .map(AuditLogService::toResponse);
    }

    private static AuditLogResponse toResponse(AuditLogEntry entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getActorId(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getDetails(),
                entry.getOccurredAt());
    }
}
