package com.schediflow.repository;

import com.schediflow.domain.CoverAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CoverAssignmentRepository extends JpaRepository<CoverAssignment, Long> {

    Optional<CoverAssignment> findByIdAndTenantId(Long id, Long tenantId);

    Optional<CoverAssignment> findByLessonIdAndTenantId(Long lessonId, Long tenantId);

    boolean existsByLessonIdAndTenantId(Long lessonId, Long tenantId);

    /**
     * Cover-teacher ids already committed to the given date and period, so a teacher is not offered
     * for — or assigned to — two lessons in the same slot.
     */
    @Query("""
            select ca.coverTeacherId
            from CoverAssignment ca, Lesson l
            where ca.tenantId = :tenantId
              and ca.lessonId = l.id
              and l.tenantId = :tenantId
              and l.scheduledDate = :scheduledDate
              and l.schedulePeriodId = :schedulePeriodId
              and l.id <> :excludeLessonId
            """)
    List<Long> findCoverTeacherIdsBusyAt(
            @Param("tenantId") Long tenantId,
            @Param("scheduledDate") LocalDate scheduledDate,
            @Param("schedulePeriodId") Long schedulePeriodId,
            @Param("excludeLessonId") Long excludeLessonId);

    /** Lessons covered per teacher within one timetable, used for workload in COVER-02. */
    @Query("""
            select ca.coverTeacherId, count(ca)
            from CoverAssignment ca, Lesson l
            where ca.tenantId = :tenantId
              and ca.lessonId = l.id
              and l.timetableId = :timetableId
            group by ca.coverTeacherId
            """)
    List<Object[]> countPerCoverTeacherInTimetable(
            @Param("tenantId") Long tenantId, @Param("timetableId") Long timetableId);
}
