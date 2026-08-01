package com.schediflow.repository;

import com.schediflow.domain.TeachingGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeachingGroupRepository extends JpaRepository<TeachingGroup, Long> {

    Optional<TeachingGroup> findByIdAndTenantIdAndActive(Long id, Long tenantId, boolean active);

    List<TeachingGroup> findByTenantIdAndActiveOrderByNameAsc(Long tenantId, boolean active);

    List<TeachingGroup> findByTenantIdAndActiveAndTeacherIdAndSubjectId(
            Long tenantId, boolean active, Long teacherId, Long subjectId);

    List<TeachingGroup> findByIdInAndTenantIdAndActive(List<Long> ids, Long tenantId, boolean active);
}
