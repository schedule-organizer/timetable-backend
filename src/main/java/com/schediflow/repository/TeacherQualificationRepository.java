package com.schediflow.repository;

import com.schediflow.domain.TeacherQualification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeacherQualificationRepository extends JpaRepository<TeacherQualification, Long> {

    List<TeacherQualification> findByTeacherIdAndTenantIdOrderByIdAsc(Long teacherId, Long tenantId);

    Optional<TeacherQualification> findByIdAndTeacherIdAndTenantId(Long id, Long teacherId, Long tenantId);

    boolean existsByTeacherIdAndTenantIdAndSubjectId(Long teacherId, Long tenantId, Long subjectId);

    List<TeacherQualification> findByTenantIdAndSubjectId(Long tenantId, Long subjectId);

    List<TeacherQualification> findByTenantId(Long tenantId);

    List<TeacherQualification> findByTenantIdAndTeacherIdIn(Long tenantId, Collection<Long> teacherIds);
}
