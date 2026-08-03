package com.schediflow.repository;

import com.schediflow.domain.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    /** Every filter is optional; a null one simply does not narrow the result. */
    @Query("""
            select a from AuditLogEntry a
            where a.tenantId = :tenantId
              and (:actorId is null or a.actorId = :actorId)
              and (:entityType is null or a.entityType = :entityType)
              and (:startDate is null or a.occurredAt >= :startDate)
              and (:endDate is null or a.occurredAt <= :endDate)
            order by a.id desc
            """)
    Page<AuditLogEntry> search(
            @Param("tenantId") Long tenantId,
            @Param("actorId") Long actorId,
            @Param("entityType") String entityType,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);
}
