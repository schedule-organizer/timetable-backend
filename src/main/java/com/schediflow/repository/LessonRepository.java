package com.schediflow.repository;

import com.schediflow.domain.Lesson;
import com.schediflow.dto.response.HolidayLessonConflictResponse;
import com.schediflow.dto.response.TimetableExportRow;
import com.schediflow.dto.response.TimetableLessonRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    List<Lesson> findByTenantIdAndTeacherUserId(Long tenantId, Long teacherUserId);

    /** Everything already scheduled in one slot of one timetable — the input to conflict detection. */
    List<Lesson> findByTenantIdAndTimetableIdAndScheduledDateAndSchedulePeriodId(
            Long tenantId, Long timetableId, LocalDate scheduledDate, Long schedulePeriodId);

    List<Lesson> findByTenantIdAndTimetableIdOrderByScheduledDateAscSchedulePeriodIdAsc(
            Long tenantId, Long timetableId);

    /**
     * The whole grid in one joined query (SCHED-02). Room is an outer join because a lesson may be
     * unroomed; the optional filters are applied with an "or the filter is null" guard so a single
     * query covers every combination.
     */
    @Query("""
            select new com.schediflow.dto.response.TimetableLessonRow(
                l.id, s.name, coalesce(t.displayName, u.displayName, u.email), r.name,
                l.schedulePeriodId, l.scheduledDate, l.pinned,
                l.teacherUserId, l.classId, l.roomId)
            from Lesson l
            join Subject s on s.id = l.subjectId and s.tenantId = l.tenantId
            join User u on u.id = l.teacherUserId and u.tenantId = l.tenantId
            left join Teacher t on t.userId = l.teacherUserId and t.tenantId = l.tenantId
            left join Room r on r.id = l.roomId and r.tenantId = l.tenantId
            where l.tenantId = :tenantId
              and l.timetableId = :timetableId
              and (:teacherUserId is null or l.teacherUserId = :teacherUserId)
              and (:classId is null or l.classId = :classId)
              and (:roomId is null or l.roomId = :roomId)
            order by l.scheduledDate asc, l.schedulePeriodId asc, l.id asc
            """)
    List<TimetableLessonRow> findGridRows(
            @Param("tenantId") Long tenantId,
            @Param("timetableId") Long timetableId,
            @Param("teacherUserId") Long teacherUserId,
            @Param("classId") Long classId,
            @Param("roomId") Long roomId);

    /**
     * Every lesson of a timetable with its names and period times resolved, ordered the way an
     * export reads: by day, then by the period's ordinal (EXPORT-01/02/03).
     *
     * <p>Ordered by ordinal rather than period *name* — names like "Period 10" and "Period 2" sort
     * wrongly as text, and ordinal is the bell schedule's own sequence.</p>
     */
    @Query("""
            select new com.schediflow.dto.response.TimetableExportRow(
                l.id, s.name, coalesce(t.displayName, u.displayName, u.email), r.name, sc.name,
                l.scheduledDate, p.name, p.ordinal, p.startTime, p.endTime,
                l.teacherUserId, l.classId, l.roomId)
            from Lesson l
            join Subject s on s.id = l.subjectId and s.tenantId = l.tenantId
            join SchoolClass sc on sc.id = l.classId and sc.tenantId = l.tenantId
            join User u on u.id = l.teacherUserId and u.tenantId = l.tenantId
            join SchedulePeriod p on p.id = l.schedulePeriodId
            left join Teacher t on t.userId = l.teacherUserId and t.tenantId = l.tenantId
            left join Room r on r.id = l.roomId and r.tenantId = l.tenantId
            where l.tenantId = :tenantId
              and l.timetableId = :timetableId
            order by l.scheduledDate asc, p.ordinal asc, sc.name asc
            """)
    List<TimetableExportRow> findExportRows(
            @Param("tenantId") Long tenantId, @Param("timetableId") Long timetableId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Lesson l where l.timetableId = :timetableId and l.tenantId = :tenantId")
    int deleteByTimetableIdAndTenantId(
            @Param("timetableId") Long timetableId, @Param("tenantId") Long tenantId);

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

    /** Lessons taught per user within one timetable, used for workload in COVER-02. */
    @Query("""
            select l.teacherUserId, count(l)
            from Lesson l
            where l.tenantId = :tenantId
              and l.timetableId = :timetableId
            group by l.teacherUserId
            """)
    List<Object[]> countPerTeacherUserInTimetable(
            @Param("tenantId") Long tenantId, @Param("timetableId") Long timetableId);

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
