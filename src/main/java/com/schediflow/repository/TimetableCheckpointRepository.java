package com.schediflow.repository;

import com.schediflow.domain.TimetableCheckpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimetableCheckpointRepository extends JpaRepository<TimetableCheckpoint, Long> {

    Optional<TimetableCheckpoint> findByIdAndTimetableIdAndTenantId(
            Long id, Long timetableId, Long tenantId);

    Page<TimetableCheckpoint> findByTenantIdAndTimetableIdOrderByIdDesc(
            Long tenantId, Long timetableId, Pageable pageable);

    /** Oldest first, so retention can trim from the front. */
    List<TimetableCheckpoint> findByTenantIdAndTimetableIdOrderByIdAsc(Long tenantId, Long timetableId);
}
