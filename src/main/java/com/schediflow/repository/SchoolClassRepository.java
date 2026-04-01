package com.schediflow.repository;

import com.schediflow.domain.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    Optional<SchoolClass> findByIdAndTenantIdAndActive(Long id, Long tenantId, boolean active);

    List<SchoolClass> findByTenantIdAndActiveOrderByNameAsc(Long tenantId, boolean active);

    boolean existsByNameAndTenantIdAndActive(String name, Long tenantId, boolean active);

    boolean existsByNameAndTenantIdAndActiveAndIdNot(String name, Long tenantId, boolean active, Long id);
}
