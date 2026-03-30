package com.schediflow.repository;

import com.schediflow.domain.BellSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BellScheduleRepository extends JpaRepository<BellSchedule, Long> {

    Optional<BellSchedule> findByIdAndTenantId(Long id, Long tenantId);

    List<BellSchedule> findByTenantIdAndDefaultScheduleTrue(Long tenantId);
}
