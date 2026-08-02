package com.schediflow.service;

import com.schediflow.domain.CoverAssignment;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.Teacher;
import com.schediflow.dto.event.CoverAssignedEvent;
import com.schediflow.dto.request.CoverAssignmentRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.CoverAssignmentRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.TenantContext;
import com.schediflow.service.cover.CoverEligibilityService;
import com.schediflow.service.cover.CoverEligibilityService.SlotAvailability;
import com.schediflow.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoverAssignmentServiceTest {

    @Mock CoverAssignmentRepository coverAssignmentRepository;
    @Mock LessonRepository lessonRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock CoverEligibilityService coverEligibilityService;
    @Mock WebSocketEventPublisher eventPublisher;
    @Mock com.schediflow.repository.UserRepository userRepository;
    @Mock com.schediflow.repository.SubjectRepository subjectRepository;
    @Mock com.schediflow.repository.SchoolClassRepository schoolClassRepository;
    @Mock EmailService emailService;

    CoverAssignmentService service;

    private static final Long TENANT_ID = 1L;
    private static final Long LESSON_ID = 50L;
    private static final Long SUBJECT_ID = 20L;
    private static final Long PERIOD_ID = 30L;
    private static final Long ORIGINAL_TEACHER_USER_ID = 500L;
    private static final Long COVER_TEACHER_ID = 11L;
    private static final Long COVER_TEACHER_USER_ID = 501L;
    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 9, 7);

    private static final JwtPrincipal MOD = new JwtPrincipal(9L, TENANT_ID, "MOD", "mod@x.edu");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new CoverAssignmentService(
                coverAssignmentRepository,
                lessonRepository,
                teacherRepository,
                coverEligibilityService,
                eventPublisher,
                userRepository,
                subjectRepository,
                schoolClassRepository,
                emailService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void assign_persistsAndPublishesEvent() {
        stubLesson();
        stubCoverTeacher();
        stubNoExistingCover();
        stubQualified(true);
        stubAvailability(free());
        stubSave();

        var response = service.assign(MOD, new CoverAssignmentRequest(LESSON_ID, COVER_TEACHER_ID, " sick "));

        assertThat(response.lessonId()).isEqualTo(LESSON_ID);
        assertThat(response.coverTeacherId()).isEqualTo(COVER_TEACHER_ID);
        assertThat(response.originalTeacherUserId()).isEqualTo(ORIGINAL_TEACHER_USER_ID);
        assertThat(response.reason()).isEqualTo("sick");
        assertThat(response.assignedBy()).isEqualTo(MOD.userId());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishToTenant(eq(TENANT_ID), payload.capture());
        CoverAssignedEvent event = (CoverAssignedEvent) payload.getValue();
        assertThat(event.event()).isEqualTo("COVER_ASSIGNED");
        assertThat(event.lessonId()).isEqualTo(LESSON_ID);
        assertThat(event.coverTeacherId()).isEqualTo(COVER_TEACHER_ID);
        assertThat(event.originalTeacherId()).isEqualTo(ORIGINAL_TEACHER_USER_ID);
    }

    @Test
    void assign_leavesTheLessonsOwnTeacherUntouched() {
        Lesson lesson = stubLesson();
        stubCoverTeacher();
        stubNoExistingCover();
        stubQualified(true);
        stubAvailability(free());
        stubSave();

        service.assign(MOD, request());

        assertThat(lesson.getTeacherUserId()).isEqualTo(ORIGINAL_TEACHER_USER_ID);
        verify(lessonRepository, never()).save(any(Lesson.class));
    }

    @Test
    void assign_blankReason_isStoredAsNull() {
        stubLesson();
        stubCoverTeacher();
        stubNoExistingCover();
        stubQualified(true);
        stubAvailability(free());
        stubSave();

        assertThat(service.assign(MOD, new CoverAssignmentRequest(LESSON_ID, COVER_TEACHER_ID, "   ")).reason())
                .isNull();
    }

    @Test
    void assign_unknownLesson_throwsNotFound() {
        when(lessonRepository.findByIdAndTenantId(LESSON_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(MOD, request())).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void assign_unknownTeacher_throwsNotFound() {
        stubLesson();
        when(teacherRepository.findByIdAndTenantIdAndActive(COVER_TEACHER_ID, TENANT_ID, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(MOD, request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assign_whenLessonAlreadyCovered_throwsConflict() {
        stubLesson();
        stubCoverTeacher();
        when(coverAssignmentRepository.existsByLessonIdAndTenantId(LESSON_ID, TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(MOD, request())).isInstanceOf(ConflictException.class);
        verify(coverAssignmentRepository, never()).save(any());
    }

    @Test
    void assign_lessonsOwnTeacher_throwsBadRequest() {
        stubLesson();
        Teacher sameTeacher = teacher(COVER_TEACHER_ID, ORIGINAL_TEACHER_USER_ID);
        when(teacherRepository.findByIdAndTenantIdAndActive(COVER_TEACHER_ID, TENANT_ID, true))
                .thenReturn(Optional.of(sameTeacher));
        stubNoExistingCover();

        assertThatThrownBy(() -> service.assign(MOD, request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own teacher");
    }

    @Test
    void assign_unqualifiedTeacher_throwsBadRequest() {
        stubLesson();
        stubCoverTeacher();
        stubNoExistingCover();
        stubQualified(false);

        assertThatThrownBy(() -> service.assign(MOD, request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not qualified");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void assign_teacherAlreadyTeachingInThatPeriod_throwsConflict() {
        stubLesson();
        stubCoverTeacher();
        stubNoExistingCover();
        stubQualified(true);
        stubAvailability(new SlotAvailability(Set.of(COVER_TEACHER_USER_ID), Set.of(), Set.of()));

        assertThatThrownBy(() -> service.assign(MOD, request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already scheduled");
    }

    @Test
    void assign_teacherAlreadyCoveringInThatPeriod_throwsConflict() {
        stubLesson();
        stubCoverTeacher();
        stubNoExistingCover();
        stubQualified(true);
        stubAvailability(new SlotAvailability(Set.of(), Set.of(COVER_TEACHER_ID), Set.of()));

        assertThatThrownBy(() -> service.assign(MOD, request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already scheduled");
    }

    @Test
    void assign_teacherWithForbiddenSlot_throwsConflict() {
        stubLesson();
        stubCoverTeacher();
        stubNoExistingCover();
        stubQualified(true);
        stubAvailability(new SlotAvailability(Set.of(), Set.of(), Set.of(COVER_TEACHER_ID)));

        assertThatThrownBy(() -> service.assign(MOD, request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("forbidden slot");
        verifyNoInteractions(eventPublisher);
    }

    // ---------- helpers ----------

    private CoverAssignmentRequest request() {
        return new CoverAssignmentRequest(LESSON_ID, COVER_TEACHER_ID, null);
    }

    private static SlotAvailability free() {
        return new SlotAvailability(Set.of(), Set.of(), Set.of());
    }

    private Lesson stubLesson() {
        Lesson lesson = new Lesson();
        ReflectionTestUtils.setField(lesson, "id", LESSON_ID);
        lesson.setTenantId(TENANT_ID);
        lesson.setSubjectId(SUBJECT_ID);
        lesson.setSchedulePeriodId(PERIOD_ID);
        lesson.setScheduledDate(LESSON_DATE);
        lesson.setTeacherUserId(ORIGINAL_TEACHER_USER_ID);
        when(lessonRepository.findByIdAndTenantId(LESSON_ID, TENANT_ID)).thenReturn(Optional.of(lesson));
        return lesson;
    }

    private void stubCoverTeacher() {
        when(teacherRepository.findByIdAndTenantIdAndActive(COVER_TEACHER_ID, TENANT_ID, true))
                .thenReturn(Optional.of(teacher(COVER_TEACHER_ID, COVER_TEACHER_USER_ID)));
    }

    private static Teacher teacher(Long id, Long userId) {
        Teacher teacher = new Teacher();
        ReflectionTestUtils.setField(teacher, "id", id);
        teacher.setUserId(userId);
        return teacher;
    }

    private void stubNoExistingCover() {
        when(coverAssignmentRepository.existsByLessonIdAndTenantId(LESSON_ID, TENANT_ID)).thenReturn(false);
    }

    private void stubQualified(boolean qualified) {
        when(coverEligibilityService.isQualified(TENANT_ID, COVER_TEACHER_ID, SUBJECT_ID)).thenReturn(qualified);
    }

    private void stubAvailability(SlotAvailability availability) {
        when(coverEligibilityService.availabilityAt(eq(TENANT_ID), any(Lesson.class))).thenReturn(availability);
    }

    private void stubSave() {
        when(coverAssignmentRepository.save(any(CoverAssignment.class))).thenAnswer(inv -> {
            CoverAssignment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 777L);
            ReflectionTestUtils.setField(saved, "assignedAt", OffsetDateTime.now());
            return saved;
        });
    }
}
