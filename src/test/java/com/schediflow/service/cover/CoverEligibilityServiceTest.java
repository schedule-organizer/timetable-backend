package com.schediflow.service.cover;

import com.schediflow.domain.ForbiddenSlot;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.Teacher;
import com.schediflow.repository.CoverAssignmentRepository;
import com.schediflow.repository.ForbiddenSlotRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TeacherQualificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoverEligibilityServiceTest {

    @Mock LessonRepository lessonRepository;
    @Mock CoverAssignmentRepository coverAssignmentRepository;
    @Mock ForbiddenSlotRepository forbiddenSlotRepository;
    @Mock TeacherQualificationRepository teacherQualificationRepository;

    CoverEligibilityService service;

    private static final Long TENANT_ID = 1L;
    private static final Long LESSON_ID = 50L;
    private static final Long PERIOD_ID = 30L;
    private static final Long TEACHER_ID = 11L;
    private static final Long TEACHER_USER_ID = 501L;
    /** 2026-09-07 is a Monday, so dayOfWeek == 1. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    @BeforeEach
    void setUp() {
        service = new CoverEligibilityService(
                lessonRepository,
                coverAssignmentRepository,
                forbiddenSlotRepository,
                teacherQualificationRepository);
    }

    @Test
    void isQualified_delegatesToQualificationRepository() {
        when(teacherQualificationRepository.existsByTeacherIdAndTenantIdAndSubjectId(TEACHER_ID, TENANT_ID, 20L))
                .thenReturn(true);

        assertThat(service.isQualified(TENANT_ID, TEACHER_ID, 20L)).isTrue();
    }

    @Test
    void teacherTeachingElsewhereInTheSlot_hasConflict() {
        stubSlot(List.of(TEACHER_USER_ID), List.of(), List.of());

        var availability = service.availabilityAt(TENANT_ID, lesson());

        assertThat(availability.hasTimetableConflict(teacher())).isTrue();
        assertThat(availability.isFree(teacher())).isFalse();
    }

    @Test
    void teacherCoveringElsewhereInTheSlot_hasConflict() {
        stubSlot(List.of(), List.of(TEACHER_ID), List.of());

        assertThat(service.availabilityAt(TENANT_ID, lesson()).hasTimetableConflict(teacher())).isTrue();
    }

    @Test
    void freeTeacher_isAvailable() {
        stubSlot(List.of(), List.of(), List.of());

        var availability = service.availabilityAt(TENANT_ID, lesson());

        assertThat(availability.hasTimetableConflict(teacher())).isFalse();
        assertThat(availability.hasForbiddenSlot(teacher())).isFalse();
        assertThat(availability.isFree(teacher())).isTrue();
    }

    @Test
    void recurringForbiddenSlotOnTheLessonsWeekday_blocksTheTeacher() {
        stubSlot(List.of(), List.of(), List.of(recurring(TEACHER_ID, 1)));

        assertThat(service.availabilityAt(TENANT_ID, lesson()).hasForbiddenSlot(teacher())).isTrue();
    }

    @Test
    void recurringForbiddenSlotOnAnotherWeekday_doesNotBlock() {
        stubSlot(List.of(), List.of(), List.of(recurring(TEACHER_ID, 3)));

        assertThat(service.availabilityAt(TENANT_ID, lesson()).hasForbiddenSlot(teacher())).isFalse();
    }

    @Test
    void oneOffForbiddenSlotOnTheLessonsDate_blocksTheTeacher() {
        stubSlot(List.of(), List.of(), List.of(oneOff(TEACHER_ID, MONDAY)));

        assertThat(service.availabilityAt(TENANT_ID, lesson()).hasForbiddenSlot(teacher())).isTrue();
    }

    @Test
    void oneOffForbiddenSlotOnAnotherDate_doesNotBlock() {
        stubSlot(List.of(), List.of(), List.of(oneOff(TEACHER_ID, MONDAY.plusDays(1))));

        assertThat(service.availabilityAt(TENANT_ID, lesson()).hasForbiddenSlot(teacher())).isFalse();
    }

    @Test
    void forbiddenSlotOfAnotherTeacher_doesNotBlock() {
        stubSlot(List.of(), List.of(), List.of(recurring(TEACHER_ID + 1, 1)));

        assertThat(service.availabilityAt(TENANT_ID, lesson()).hasForbiddenSlot(teacher())).isFalse();
    }

    // ---------- helpers ----------

    private void stubSlot(List<Long> busyUserIds, List<Long> busyCoverTeacherIds, List<ForbiddenSlot> slots) {
        when(lessonRepository.findTeacherUserIdsBusyAt(TENANT_ID, MONDAY, PERIOD_ID, LESSON_ID))
                .thenReturn(busyUserIds);
        when(coverAssignmentRepository.findCoverTeacherIdsBusyAt(TENANT_ID, MONDAY, PERIOD_ID, LESSON_ID))
                .thenReturn(busyCoverTeacherIds);
        when(forbiddenSlotRepository.findByTenantIdAndEntityTypeAndSchedulePeriodId(
                        TENANT_ID, "TEACHER", PERIOD_ID))
                .thenReturn(slots);
    }

    private static Lesson lesson() {
        Lesson lesson = new Lesson();
        ReflectionTestUtils.setField(lesson, "id", LESSON_ID);
        lesson.setTenantId(TENANT_ID);
        lesson.setSchedulePeriodId(PERIOD_ID);
        lesson.setScheduledDate(MONDAY);
        return lesson;
    }

    private static Teacher teacher() {
        Teacher teacher = new Teacher();
        ReflectionTestUtils.setField(teacher, "id", TEACHER_ID);
        teacher.setUserId(TEACHER_USER_ID);
        return teacher;
    }

    private static ForbiddenSlot recurring(Long teacherId, int dayOfWeek) {
        ForbiddenSlot slot = new ForbiddenSlot();
        slot.setEntityType("TEACHER");
        slot.setEntityId(teacherId);
        slot.setSchedulePeriodId(PERIOD_ID);
        slot.setRecurring(true);
        slot.setDayOfWeek(dayOfWeek);
        return slot;
    }

    private static ForbiddenSlot oneOff(Long teacherId, LocalDate date) {
        ForbiddenSlot slot = new ForbiddenSlot();
        slot.setEntityType("TEACHER");
        slot.setEntityId(teacherId);
        slot.setSchedulePeriodId(PERIOD_ID);
        slot.setRecurring(false);
        slot.setSpecificDate(date);
        return slot;
    }
}
