package com.schediflow.repository;

import com.schediflow.domain.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    Optional<Timetable> findByIdAndTenantId(Long id, Long tenantId);
}
