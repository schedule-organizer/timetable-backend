package com.schediflow.repository;

import com.schediflow.domain.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    /**
     * Tenant-scoped PK lookup. Use instead of findById to ensure cross-tenant access returns empty.
     * Note: Hibernate's session.find() (used by findById) bypasses @Filter — this derived query does not.
     */
    Optional<AcademicYear> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * Finds all active academic years for the given tenant.
     * Used by the service to deactivate the current active year before activating another.
     */
    List<AcademicYear> findByTenantIdAndActiveTrue(Long tenantId);
}
