package com.schediflow.repository;

import com.schediflow.domain.SolverJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolverJobRepository extends JpaRepository<SolverJob, Long> {

    Optional<SolverJob> findByIdAndTenantId(Long id, Long tenantId);

    Page<SolverJob> findByTenantIdAndTimetableIdOrderByIdDesc(
            Long tenantId, Long timetableId, Pageable pageable);

    Page<SolverJob> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);

    List<SolverJob> findByTimetableIdAndStatusIn(Long timetableId, List<String> statuses);
}
