package com.schediflow.repository;

import com.schediflow.domain.TemporaryScheduleLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TemporaryScheduleLessonRepository extends JpaRepository<TemporaryScheduleLesson, Long> {

    List<TemporaryScheduleLesson> findByTemporaryScheduleIdOrderByIdAsc(Long temporaryScheduleId);

    long countByTemporaryScheduleId(Long temporaryScheduleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TemporaryScheduleLesson tsl where tsl.temporaryScheduleId = :temporaryScheduleId")
    int deleteAllByTemporaryScheduleId(@Param("temporaryScheduleId") Long temporaryScheduleId);
}
