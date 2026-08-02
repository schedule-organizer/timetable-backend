package com.schediflow.service;

import com.schediflow.domain.ConflictType;
import com.schediflow.domain.ForbiddenSlot;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.Room;
import com.schediflow.domain.SchoolClass;
import com.schediflow.domain.Teacher;
import com.schediflow.dto.response.ConflictViolation;
import com.schediflow.repository.ForbiddenSlotRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.TermRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SCHED-11 real-time conflict checks. HOL-07's holiday query is covered by
 * {@link ConflictDetectionServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConflictDetectionServiceSchedTest {

    @Mock TermRepository termRepository;
    @Mock LessonRepository lessonRepository;
    @Mock RoomRepository roomRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock ForbiddenSlotRepository forbiddenSlotRepository;

    ConflictDetectionService service;

    private static final Long TENANT = 1L;
    private static final Long TIMETABLE = 5L;
    private static final Long PERIOD = 30L;
    private static final Long OTHER_PERIOD = 31L;
    private static final Long TEACHER_USER = 500L;
    private static final Long TEACHER_ID = 11L;
    private static final Long CLASS_ID = 60L;
    private static final Long ROOM_ID = 70L;
    /** 2026-09-07 is a Monday. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    @BeforeEach
    void setUp() {
        service = new ConflictDetectionService(
                termRepository, lessonRepository, roomRepository,
                schoolClassRepository, teacherRepository, forbiddenSlotRepository);
        stubSlot(List.of());
        stubForbidden(List.of());
        when(teacherRepository.findByUserIdAndTenantId(TEACHER_USER, TENANT))
                .thenReturn(Optional.of(teacher()));
    }

    @Test
    void cleanSlot_hasNoViolations() {
        assertThat(service.checkConflicts(lesson(1L), null, null)).isEmpty();
        assertThat(service.hasConflict(lesson(1L))).isFalse();
    }

    @Test
    void teacherAlreadyTeachingInSlot_isReported() {
        Lesson other = lesson(2L);
        other.setClassId(CLASS_ID + 1);
        stubSlot(List.of(other));

        List<ConflictViolation> violations = service.checkConflicts(lesson(1L), null, null);

        assertThat(violations).extracting(ConflictViolation::type)
                .containsExactly(ConflictType.TEACHER_DOUBLE_BOOKED);
        assertThat(violations.get(0).conflictingLessonId()).isEqualTo(2L);
    }

    @Test
    void classAlreadyScheduledInSlot_isReported() {
        Lesson other = lesson(2L);
        other.setTeacherUserId(TEACHER_USER + 1);
        stubSlot(List.of(other));

        assertThat(service.checkConflicts(lesson(1L), null, null))
                .extracting(ConflictViolation::type)
                .containsExactly(ConflictType.CLASS_DOUBLE_BOOKED);
    }

    @Test
    void roomAlreadyBookedInSlot_isReported() {
        Lesson other = lesson(2L);
        other.setTeacherUserId(TEACHER_USER + 1);
        other.setClassId(CLASS_ID + 1);
        other.setRoomId(ROOM_ID);
        stubSlot(List.of(other));

        Lesson moving = lesson(1L);
        moving.setRoomId(ROOM_ID);

        assertThat(service.checkConflicts(moving, null, null))
                .extracting(ConflictViolation::type)
                .containsExactly(ConflictType.ROOM_DOUBLE_BOOKED);
    }

    @Test
    void theLessonItselfIsNeverItsOwnConflict() {
        Lesson self = lesson(1L);
        self.setRoomId(ROOM_ID);
        stubSlot(List.of(self));

        assertThat(service.checkConflicts(self, null, null)).isEmpty();
    }

    @Test
    void oneOccupantCanTriggerSeveralViolationsAtOnce() {
        Lesson other = lesson(2L);
        other.setRoomId(ROOM_ID);
        stubSlot(List.of(other));

        Lesson moving = lesson(1L);
        moving.setRoomId(ROOM_ID);

        assertThat(service.checkConflicts(moving, null, null))
                .extracting(ConflictViolation::type)
                .containsExactlyInAnyOrder(
                        ConflictType.TEACHER_DOUBLE_BOOKED,
                        ConflictType.CLASS_DOUBLE_BOOKED,
                        ConflictType.ROOM_DOUBLE_BOOKED);
    }

    @Test
    void proposedPeriodIsCheckedInsteadOfTheCurrentOne() {
        Lesson other = lesson(2L);
        other.setClassId(CLASS_ID + 1);
        when(lessonRepository.findByTenantIdAndTimetableIdAndScheduledDateAndSchedulePeriodId(
                        TENANT, TIMETABLE, MONDAY, OTHER_PERIOD))
                .thenReturn(List.of(other));

        assertThat(service.checkConflicts(lesson(1L), OTHER_PERIOD, null))
                .extracting(ConflictViolation::type)
                .containsExactly(ConflictType.TEACHER_DOUBLE_BOOKED);
    }

    @Test
    void roomTooSmallForTheClass_isReported() {
        stubRoom(20);
        stubClass(30);

        assertThat(service.checkConflicts(lesson(1L), null, ROOM_ID))
                .extracting(ConflictViolation::type)
                .containsExactly(ConflictType.ROOM_CAPACITY_EXCEEDED);
    }

    @Test
    void roomLargeEnough_isFine() {
        stubRoom(40);
        stubClass(30);

        assertThat(service.checkConflicts(lesson(1L), null, ROOM_ID)).isEmpty();
    }

    @Test
    void unknownCapacities_areNotTreatedAsViolations() {
        stubRoom(null);
        stubClass(30);
        assertThat(service.checkConflicts(lesson(1L), null, ROOM_ID)).isEmpty();

        stubRoom(20);
        stubClass(null);
        assertThat(service.checkConflicts(lesson(1L), null, ROOM_ID)).isEmpty();
    }

    @Test
    void noRoom_skipsRoomChecksEntirely() {
        assertThat(service.checkConflicts(lesson(1L), null, null)).isEmpty();
    }

    @Test
    void recurringTeacherForbiddenSlotOnThatWeekday_isReported() {
        stubForbidden(List.of(recurring("TEACHER", TEACHER_ID, 1)));

        assertThat(service.checkConflicts(lesson(1L), null, null))
                .extracting(ConflictViolation::type)
                .containsExactly(ConflictType.TEACHER_FORBIDDEN_SLOT);
    }

    @Test
    void forbiddenSlotOnADifferentWeekday_isIgnored() {
        stubForbidden(List.of(recurring("TEACHER", TEACHER_ID, 3)));

        assertThat(service.checkConflicts(lesson(1L), null, null)).isEmpty();
    }

    @Test
    void oneOffForbiddenSlotOnThatDate_isReported() {
        stubForbidden(List.of(oneOff("CLASS", CLASS_ID, MONDAY)));

        assertThat(service.checkConflicts(lesson(1L), null, null))
                .extracting(ConflictViolation::type)
                .containsExactly(ConflictType.CLASS_FORBIDDEN_SLOT);
    }

    @Test
    void roomForbiddenSlot_isReportedForTheProposedRoom() {
        stubForbidden(List.of(recurring("ROOM", ROOM_ID, 1)));
        stubRoom(40);
        stubClass(30);

        assertThat(service.checkConflicts(lesson(1L), null, ROOM_ID))
                .extracting(ConflictViolation::type)
                .containsExactly(ConflictType.ROOM_FORBIDDEN_SLOT);
    }

    @Test
    void forbiddenSlotForAnotherEntity_isIgnored() {
        stubForbidden(List.of(recurring("TEACHER", TEACHER_ID + 99, 1)));

        assertThat(service.checkConflicts(lesson(1L), null, null)).isEmpty();
    }

    @Test
    void unparseableEntityType_isSkippedRatherThanThrowing() {
        stubForbidden(List.of(recurring("BUILDING", 1L, 1)));

        assertThat(service.checkConflicts(lesson(1L), null, null)).isEmpty();
    }

    @Test
    void teacherWithoutAProfile_skipsTeacherForbiddenChecks() {
        when(teacherRepository.findByUserIdAndTenantId(TEACHER_USER, TENANT)).thenReturn(Optional.empty());
        stubForbidden(List.of(recurring("TEACHER", TEACHER_ID, 1)));

        assertThat(service.checkConflicts(lesson(1L), null, null)).isEmpty();
    }

    // ---------- fixture ----------

    private static Lesson lesson(Long id) {
        Lesson lesson = new Lesson();
        ReflectionTestUtils.setField(lesson, "id", id);
        lesson.setTenantId(TENANT);
        lesson.setTimetableId(TIMETABLE);
        lesson.setClassId(CLASS_ID);
        lesson.setTeacherUserId(TEACHER_USER);
        lesson.setSchedulePeriodId(PERIOD);
        lesson.setScheduledDate(MONDAY);
        return lesson;
    }

    private static Teacher teacher() {
        Teacher teacher = new Teacher();
        ReflectionTestUtils.setField(teacher, "id", TEACHER_ID);
        teacher.setUserId(TEACHER_USER);
        return teacher;
    }

    private void stubSlot(List<Lesson> occupants) {
        when(lessonRepository.findByTenantIdAndTimetableIdAndScheduledDateAndSchedulePeriodId(
                        TENANT, TIMETABLE, MONDAY, PERIOD))
                .thenReturn(occupants);
    }

    private void stubForbidden(List<ForbiddenSlot> slots) {
        when(forbiddenSlotRepository.findByTenantIdAndSchedulePeriodId(TENANT, PERIOD)).thenReturn(slots);
        when(forbiddenSlotRepository.findByTenantIdAndSchedulePeriodId(TENANT, OTHER_PERIOD))
                .thenReturn(List.of());
    }

    private void stubRoom(Integer capacity) {
        Room room = new Room();
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        room.setCapacity(capacity);
        when(roomRepository.findByIdAndTenantId(ROOM_ID, TENANT)).thenReturn(Optional.of(room));
    }

    private void stubClass(Integer capacity) {
        SchoolClass schoolClass = new SchoolClass();
        ReflectionTestUtils.setField(schoolClass, "id", CLASS_ID);
        schoolClass.setCapacity(capacity);
        when(schoolClassRepository.findByIdAndTenantIdAndActive(CLASS_ID, TENANT, true))
                .thenReturn(Optional.of(schoolClass));
    }

    private static ForbiddenSlot recurring(String entityType, Long entityId, int dayOfWeek) {
        ForbiddenSlot slot = new ForbiddenSlot();
        slot.setEntityType(entityType);
        slot.setEntityId(entityId);
        slot.setSchedulePeriodId(PERIOD);
        slot.setRecurring(true);
        slot.setDayOfWeek(dayOfWeek);
        return slot;
    }

    private static ForbiddenSlot oneOff(String entityType, Long entityId, LocalDate date) {
        ForbiddenSlot slot = new ForbiddenSlot();
        slot.setEntityType(entityType);
        slot.setEntityId(entityId);
        slot.setSchedulePeriodId(PERIOD);
        slot.setRecurring(false);
        slot.setSpecificDate(date);
        return slot;
    }
}
