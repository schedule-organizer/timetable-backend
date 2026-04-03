package com.schediflow.repository;

import com.schediflow.domain.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    List<Teacher> findByTenantIdAndActiveOrderByDisplayNameAsc(Long tenantId, boolean active);

    Optional<Teacher> findByIdAndTenantIdAndActive(Long id, Long tenantId, boolean active);

    boolean existsByUserIdAndTenantId(Long userId, Long tenantId);

    boolean existsByUserIdAndTenantIdAndIdNot(Long userId, Long tenantId, Long id);
}
