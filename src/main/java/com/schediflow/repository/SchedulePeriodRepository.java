package com.schediflow.repository;

import com.schediflow.domain.SchedulePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Long> {

    List<SchedulePeriod> findByBellScheduleIdOrderByOrdinalAsc(Long bellScheduleId);

    @Modifying
    @Query("DELETE FROM SchedulePeriod sp WHERE sp.bellScheduleId = :bellScheduleId")
    void deleteAllByBellScheduleId(@Param("bellScheduleId") Long bellScheduleId);
}
