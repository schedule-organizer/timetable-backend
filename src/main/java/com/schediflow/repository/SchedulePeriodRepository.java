package com.schediflow.repository;

import com.schediflow.domain.SchedulePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Long> {

    List<SchedulePeriod> findByBellScheduleIdOrderByOrdinalAsc(Long bellScheduleId);

    Optional<SchedulePeriod> findByIdAndTenantId(Long id, Long tenantId);

    @Modifying
    @Query("DELETE FROM SchedulePeriod sp WHERE sp.bellScheduleId = :bellScheduleId")
    void deleteAllByBellScheduleId(@Param("bellScheduleId") Long bellScheduleId);
}
