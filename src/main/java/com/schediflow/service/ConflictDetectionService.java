package com.schediflow.service;

import com.schediflow.domain.ConflictType;
import com.schediflow.domain.ForbiddenSlot;
import com.schediflow.domain.ForbiddenSlotEntityType;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.Room;
import com.schediflow.domain.SchoolClass;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.Term;
import com.schediflow.dto.response.ConflictViolation;
import com.schediflow.dto.response.HolidayLessonConflictResponse;
import com.schediflow.repository.ForbiddenSlotRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Detects scheduling conflicts.
 *
 * <ul>
 *   <li>HOL-07 — published lessons falling on a date that becomes a holiday.</li>
 *   <li>SCHED-11 — real-time checks for a proposed lesson position, used by the grid (SCHED-02),
 *       moves (SCHED-08) and swaps (SCHED-10).</li>
 * </ul>
 *
 * <p>Read-only: it never writes, so a caller can ask "what would break?" before committing.</p>
 */
@Service
public class ConflictDetectionService {

    private final TermRepository termRepository;
    private final LessonRepository lessonRepository;
    private final RoomRepository roomRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final TeacherRepository teacherRepository;
    private final ForbiddenSlotRepository forbiddenSlotRepository;

    public ConflictDetectionService(
            TermRepository termRepository,
            LessonRepository lessonRepository,
            RoomRepository roomRepository,
            SchoolClassRepository schoolClassRepository,
            TeacherRepository teacherRepository,
            ForbiddenSlotRepository forbiddenSlotRepository) {
        this.termRepository = termRepository;
        this.lessonRepository = lessonRepository;
        this.roomRepository = roomRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.teacherRepository = teacherRepository;
        this.forbiddenSlotRepository = forbiddenSlotRepository;
    }

    /**
     * Finds published lessons in the given academic year that fall on {@code holidayDate} (warnings only).
     */
    @Transactional(readOnly = true)
    public List<HolidayLessonConflictResponse> findPublishedLessonHolidayConflicts(
            Long tenantId, Long academicYearId, LocalDate holidayDate) {
        List<Long> termIds = termRepository.findByAcademicYearIdAndTenantIdOrderByOrdinalAsc(academicYearId, tenantId).stream()
                .filter(term -> !holidayDate.isBefore(term.getStartDate()) && !holidayDate.isAfter(term.getEndDate()))
                .map(Term::getId)
                .toList();
        if (termIds.isEmpty()) {
            return List.of();
        }
        return lessonRepository.findPublishedConflictsOnDate(tenantId, holidayDate, termIds);
    }

    /**
     * Everything wrong with putting {@code lesson} in the proposed period and room.
     *
     * <p>Both proposals are optional; a null means "keep what the lesson already has", so the same
     * call answers "is it OK where it is?" and "would this move be OK?".</p>
     *
     * <p>Two queries regardless of school size: the occupants of the target slot, and the forbidden
     * slots on that period.</p>
     *
     * @return the violations, empty when the position is clean
     */
    @Transactional(readOnly = true)
    public List<ConflictViolation> checkConflicts(
            Lesson lesson, Long proposedPeriodId, Long proposedRoomId) {

        Long tenantId = lesson.getTenantId();
        Long periodId = proposedPeriodId != null ? proposedPeriodId : lesson.getSchedulePeriodId();
        Long roomId = proposedRoomId != null ? proposedRoomId : lesson.getRoomId();

        List<ConflictViolation> violations = new ArrayList<>();
        List<Lesson> occupants =
                lessonRepository.findByTenantIdAndTimetableIdAndScheduledDateAndSchedulePeriodId(
                        tenantId, lesson.getTimetableId(), lesson.getScheduledDate(), periodId);

        collectOccupancyConflicts(lesson, occupants, roomId, violations);
        checkRoomCapacity(tenantId, lesson, roomId, violations);
        applyForbiddenSlots(
                tenantId,
                lesson,
                roomId,
                forbiddenSlotRepository.findByTenantIdAndSchedulePeriodId(tenantId, periodId),
                violations);
        return violations;
    }

    /** Who else is in the slot, and does any of it clash with this lesson? */
    private static void collectOccupancyConflicts(
            Lesson lesson, List<Lesson> occupants, Long roomId, List<ConflictViolation> violations) {
        for (Lesson other : occupants) {
            if (Objects.equals(other.getId(), lesson.getId())) {
                continue;
            }
            if (Objects.equals(other.getTeacherUserId(), lesson.getTeacherUserId())) {
                violations.add(ConflictViolation.of(
                        ConflictType.TEACHER_DOUBLE_BOOKED,
                        "Teacher is already teaching another lesson in this period",
                        other.getId()));
            }
            if (Objects.equals(other.getClassId(), lesson.getClassId())) {
                violations.add(ConflictViolation.of(
                        ConflictType.CLASS_DOUBLE_BOOKED,
                        "Class already has a lesson in this period",
                        other.getId()));
            }
            if (roomId != null && Objects.equals(other.getRoomId(), roomId)) {
                violations.add(ConflictViolation.of(
                        ConflictType.ROOM_DOUBLE_BOOKED,
                        "Room is already booked in this period",
                        other.getId()));
            }
        }
    }

    /** Convenience for callers that only need a yes/no, such as the grid's {@code hasConflict} flag. */
    @Transactional(readOnly = true)
    public boolean hasConflict(Lesson lesson) {
        return !checkConflicts(lesson, null, null).isEmpty();
    }

    /**
     * Conflict flags for an entire timetable in one pass (SCHED-02).
     *
     * <p>Calling {@link #checkConflicts} per lesson would be N queries; this loads every lesson and
     * every forbidden slot once and reuses the same comparison rules, so the grid flag can never
     * disagree with what a move or swap would report.</p>
     *
     * @return lesson id → whether that lesson currently conflicts
     */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> hasConflictByLessonId(Long tenantId, Long timetableId) {
        List<Lesson> lessons =
                lessonRepository.findByTenantIdAndTimetableIdOrderByScheduledDateAscSchedulePeriodIdAsc(
                        tenantId, timetableId);
        if (lessons.isEmpty()) {
            return Map.of();
        }

        Map<SlotKey, List<Lesson>> bySlot = lessons.stream()
                .collect(Collectors.groupingBy(SlotKey::of));
        Map<Long, List<ForbiddenSlot>> forbiddenByPeriod = bySlot.keySet().stream()
                .map(SlotKey::schedulePeriodId)
                .distinct()
                .collect(Collectors.toMap(
                        periodId -> periodId,
                        periodId -> forbiddenSlotRepository.findByTenantIdAndSchedulePeriodId(tenantId, periodId)));

        Map<Long, Boolean> result = new HashMap<>();
        for (Lesson lesson : lessons) {
            List<ConflictViolation> violations = new ArrayList<>();
            collectOccupancyConflicts(
                    lesson, bySlot.getOrDefault(SlotKey.of(lesson), List.of()), lesson.getRoomId(), violations);
            checkRoomCapacity(tenantId, lesson, lesson.getRoomId(), violations);
            applyForbiddenSlots(
                    tenantId,
                    lesson,
                    lesson.getRoomId(),
                    forbiddenByPeriod.getOrDefault(lesson.getSchedulePeriodId(), List.of()),
                    violations);
            result.put(lesson.getId(), !violations.isEmpty());
        }
        return result;
    }

    /** A lesson's position in the week — the unit that can only hold one teacher, class or room. */
    private record SlotKey(LocalDate scheduledDate, Long schedulePeriodId) {

        static SlotKey of(Lesson lesson) {
            return new SlotKey(lesson.getScheduledDate(), lesson.getSchedulePeriodId());
        }
    }

    private void checkRoomCapacity(
            Long tenantId, Lesson lesson, Long roomId, List<ConflictViolation> violations) {
        if (roomId == null) {
            return;
        }
        Room room = roomRepository.findByIdAndTenantId(roomId, tenantId).orElse(null);
        SchoolClass schoolClass =
                schoolClassRepository.findByIdAndTenantIdAndActive(lesson.getClassId(), tenantId, true)
                        .orElse(null);
        if (room == null || schoolClass == null) {
            return;
        }
        // Only comparable when both numbers are known; an unset capacity is not a violation.
        Integer capacity = room.getCapacity();
        Integer groupSize = schoolClass.getCapacity();
        if (capacity != null && groupSize != null && capacity < groupSize) {
            violations.add(ConflictViolation.of(
                    ConflictType.ROOM_CAPACITY_EXCEEDED,
                    "Room holds " + capacity + " but the class has " + groupSize));
        }
    }

    private void applyForbiddenSlots(
            Long tenantId,
            Lesson lesson,
            Long roomId,
            List<ForbiddenSlot> slots,
            List<ConflictViolation> violations) {

        if (slots.isEmpty()) {
            return;
        }

        // forbidden_slots identifies a teacher by teachers.id, while a lesson carries users.id.
        Long teacherId = teacherRepository
                .findByUserIdAndTenantId(lesson.getTeacherUserId(), tenantId)
                .map(Teacher::getId)
                .orElse(null);
        int dayOfWeek = lesson.getScheduledDate().getDayOfWeek().getValue();

        for (ForbiddenSlot slot : slots) {
            if (!appliesOn(slot, lesson.getScheduledDate(), dayOfWeek)) {
                continue;
            }
            ForbiddenSlotEntityType entityType = parseEntityType(slot.getEntityType());
            if (entityType == null) {
                continue;
            }
            switch (entityType) {
                case TEACHER -> {
                    if (teacherId != null && Objects.equals(slot.getEntityId(), teacherId)) {
                        violations.add(ConflictViolation.of(
                                ConflictType.TEACHER_FORBIDDEN_SLOT,
                                "Teacher is unavailable in this period"));
                    }
                }
                case ROOM -> {
                    if (roomId != null && Objects.equals(slot.getEntityId(), roomId)) {
                        violations.add(ConflictViolation.of(
                                ConflictType.ROOM_FORBIDDEN_SLOT,
                                "Room is unavailable in this period"));
                    }
                }
                case CLASS -> {
                    if (Objects.equals(slot.getEntityId(), lesson.getClassId())) {
                        violations.add(ConflictViolation.of(
                                ConflictType.CLASS_FORBIDDEN_SLOT,
                                "Class is unavailable in this period"));
                    }
                }
            }
        }
    }

    private static boolean appliesOn(ForbiddenSlot slot, LocalDate date, int dayOfWeek) {
        if (slot.isRecurring()) {
            return slot.getDayOfWeek() != null && slot.getDayOfWeek() == dayOfWeek;
        }
        return date.equals(slot.getSpecificDate());
    }

    private static ForbiddenSlotEntityType parseEntityType(String raw) {
        try {
            return ForbiddenSlotEntityType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
