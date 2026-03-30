package com.schediflow.repository;

import com.schediflow.domain.HolidayCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, Long> {

    /**
     * Tenant-scoped PK lookup. Use instead of findById to ensure cross-tenant access returns empty.
     */
    Optional<HolidayCalendar> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * Checks whether a calendar already exists for a given academic year within a tenant.
     * Used to enforce the one-calendar-per-academic-year-per-tenant constraint.
     */
    boolean existsByAcademicYearIdAndTenantId(Long academicYearId, Long tenantId);

    /**
     * Same uniqueness check but excluding a specific calendar id — used during update.
     */
    boolean existsByAcademicYearIdAndTenantIdAndIdNot(Long academicYearId, Long tenantId, Long id);
}
