package com.schediflow.repository;

import com.schediflow.domain.HolidayDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HolidayDateRepository extends JpaRepository<HolidayDate, Long> {

    List<HolidayDate> findByHolidayCalendarId(Long holidayCalendarId);
}
