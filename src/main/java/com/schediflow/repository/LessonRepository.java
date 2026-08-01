package com.schediflow.repository;

import com.schediflow.domain.Lesson;
import com.schediflow.dto.response.HolidayLessonConflictResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    boolean existsByClassIdAndTenantId(Long classId, Long tenantId);

    boolean existsByTeacherUserIdAndTenantId(Long teacherUserId, Long tenantId);

    Optional<Lesson> findByIdAndTenantId(Long id, Long tenantId);

    List<Lesson> findByIdInAndTenantId(Collection<Long> ids, Long tenantId);

    /** Teacher user ids already teaching in the given slot, ignoring the lesson being covered. */
    @Query("""
            select l.teacherUserId
            from Lesson l
            where l.tenantId = :tenantId
              and l.scheduledDate = :scheduledDate
              and l.schedulePeriodId = :schedulePeriodId
              and l.id <> :excludeLessonId
            """)
    List<Long> findTeacherUserIdsBusyAt(
            @Param("tenantId") Long tenantId,
            @Param("scheduledDate") LocalDate scheduledDate,
            @Param("schedulePeriodId") Long schedulePeriodId,
            @Param("excludeLessonId") Long excludeLessonId);

    /** Lesson counts per teaching user, used for workload in COVER-02. */
    @Query("""
            select l.teacherUserId, count(l)
            from Lesson l
            where l.tenantId = :tenantId
            group by l.teacherUserId
            """)
    List<Object[]> countPerTeacherUser(@Param("tenantId") Long tenantId);

    @Query("""
            SELECT new com.schediflow.dto.response.HolidayLessonConflictResponse(
                l.id, s.name, COALESCE(u.displayName, u.email), sc.name, :holidayDate)
            FROM Lesson l, Timetable t, Subject s, SchoolClass sc, User u
            WHERE l.tenantId = :tenantId
              AND l.scheduledDate = :holidayDate
              AND l.timetableId = t.id
              AND t.tenantId = :tenantId
              AND t.status = 'PUBLISHED'
              AND t.termId IN :termIds
              AND l.subjectId = s.id
              AND s.tenantId = :tenantId
              AND l.classId = sc.id
              AND sc.tenantId = :tenantId
              AND l.teacherUserId = u.id
              AND u.tenantId = :tenantId
            ORDER BY l.id
            """)
    List<HolidayLessonConflictResponse> findPublishedConflictsOnDate(
            @Param("tenantId") Long tenantId,
            @Param("holidayDate") LocalDate holidayDate,
            @Param("termIds") List<Long> termIds);
}
