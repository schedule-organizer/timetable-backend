package com.schediflow.repository;

import com.schediflow.domain.TemporarySchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TemporaryScheduleRepository extends JpaRepository<TemporarySchedule, Long> {

    Optional<TemporarySchedule> findByIdAndTenantId(Long id, Long tenantId);

    List<TemporarySchedule> findByTenantIdOrderByStartDateAsc(Long tenantId);

    List<TemporarySchedule> findByTenantIdAndBaseTimetableIdAndStatus(
            Long tenantId, Long baseTimetableId, String status);

    /** Overlays whose window has closed, for the expiry job (COVER-06). */
    List<TemporarySchedule> findByStatusAndEndDateLessThanOrderByIdAsc(String status, LocalDate date);
}
