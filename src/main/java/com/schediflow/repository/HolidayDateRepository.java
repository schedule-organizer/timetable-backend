package com.schediflow.repository;

import com.schediflow.domain.HolidayDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayDateRepository extends JpaRepository<HolidayDate, Long> {

    List<HolidayDate> findByHolidayCalendarId(Long holidayCalendarId);

    List<HolidayDate> findByHolidayCalendarIdOrderByDateAsc(Long holidayCalendarId);

    Optional<HolidayDate> findByHolidayCalendarIdAndTenantIdAndDate(
            Long holidayCalendarId, Long tenantId, LocalDate date);

    /**
     * Tenant-scoped lookup of a single holiday date within a specific calendar.
     */
    Optional<HolidayDate> findByIdAndHolidayCalendarIdAndTenantId(
            Long id, Long holidayCalendarId, Long tenantId);

    /**
     * Checks for a duplicate date within the same calendar and tenant.
     * Used to enforce the one-date-per-calendar constraint before insert.
     */
    boolean existsByHolidayCalendarIdAndTenantIdAndDate(
            Long holidayCalendarId, Long tenantId, LocalDate date);
}
