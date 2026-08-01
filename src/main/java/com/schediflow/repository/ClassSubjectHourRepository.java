package com.schediflow.repository;

import com.schediflow.domain.ClassSubjectHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClassSubjectHourRepository extends JpaRepository<ClassSubjectHour, Long> {

    boolean existsBySubjectId(Long subjectId);

    boolean existsByClassId(Long classId);

    List<ClassSubjectHour> findByTenantIdAndClassIdOrderBySubjectIdAsc(Long tenantId, Long classId);

    @Modifying(clearAutomatically = true)
    @Query("delete from ClassSubjectHour c where c.tenantId = :tenantId and c.classId = :classId")
    void deleteByTenantIdAndClassId(@Param("tenantId") Long tenantId, @Param("classId") Long classId);
}
