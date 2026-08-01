package com.schediflow.repository;

import com.schediflow.domain.TeachingGroupClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TeachingGroupClassRepository extends JpaRepository<TeachingGroupClass, Long> {

    List<TeachingGroupClass> findByTeachingGroupIdOrderByClassIdAsc(Long teachingGroupId);

    List<TeachingGroupClass> findByTeachingGroupIdInOrderByClassIdAsc(Collection<Long> teachingGroupIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeachingGroupClass tgc where tgc.teachingGroupId = :teachingGroupId")
    void deleteAllByTeachingGroupId(@Param("teachingGroupId") Long teachingGroupId);
}
