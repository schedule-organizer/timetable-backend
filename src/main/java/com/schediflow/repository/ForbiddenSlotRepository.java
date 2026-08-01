package com.schediflow.repository;

import com.schediflow.domain.ForbiddenSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ForbiddenSlotRepository extends JpaRepository<ForbiddenSlot, Long> {

    Optional<ForbiddenSlot> findByIdAndTenantId(Long id, Long tenantId);

    List<ForbiddenSlot> findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
            Long tenantId, String entityType, Long entityId);

    List<ForbiddenSlot> findByTenantIdOrderByIdAsc(Long tenantId);

    List<ForbiddenSlot> findByTenantIdAndEntityTypeAndSchedulePeriodId(
            Long tenantId, String entityType, Long schedulePeriodId);
}
