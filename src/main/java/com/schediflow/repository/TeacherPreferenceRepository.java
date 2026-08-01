package com.schediflow.repository;

import com.schediflow.domain.TeacherPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherPreferenceRepository extends JpaRepository<TeacherPreference, Long> {

    List<TeacherPreference> findByTenantIdAndTeacherIdOrderByIdAsc(Long tenantId, Long teacherId);
}
