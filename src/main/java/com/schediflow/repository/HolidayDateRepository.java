package com.schediflow.repository;

import com.schediflow.domain.HolidayDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayDateRepository extends JpaRepository<HolidayDate, Long> {

    List<HolidayDate> findByHolidayCalendarId(Long holidayCalendarId);

    Optional<HolidayDate> findByHolidayCalendarIdAndTenantIdAndDate(
            Long holidayCalendarId, Long tenantId, LocalDate date);
}
