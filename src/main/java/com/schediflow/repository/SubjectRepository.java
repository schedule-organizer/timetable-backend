package com.schediflow.repository;

import com.schediflow.domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findByIdAndTenantIdAndActive(Long id, Long tenantId, boolean active);

    List<Subject> findByTenantIdAndActiveOrderByNameAsc(Long tenantId, boolean active);

    boolean existsByCodeAndTenantIdAndActive(String code, Long tenantId, boolean active);

    boolean existsByCodeAndTenantIdAndActiveAndIdNot(String code, Long tenantId, boolean active, Long id);
}
