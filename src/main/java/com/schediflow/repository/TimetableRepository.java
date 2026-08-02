package com.schediflow.repository;

import com.schediflow.domain.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    Optional<Timetable> findByIdAndTenantId(Long id, Long tenantId);

    List<Timetable> findByTenantIdOrderByIdAsc(Long tenantId);

    List<Timetable> findByTenantIdAndTermIdOrderByIdAsc(Long tenantId, Long termId);

    List<Timetable> findByTenantIdAndStatusOrderByIdAsc(Long tenantId, String status);

    List<Timetable> findByTenantIdAndTermIdAndStatusOrderByIdAsc(Long tenantId, Long termId, String status);

    /** Drafts whose scheduled publication time has arrived (SCHED-07). */
    List<Timetable> findByStatusAndPublishAtLessThanEqual(String status, java.time.OffsetDateTime at);
}
