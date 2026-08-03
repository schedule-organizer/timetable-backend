package com.schediflow.service;

import com.schediflow.domain.Lesson;
import com.schediflow.domain.Teacher;
import com.schediflow.dto.event.LessonUpdatedEvent;
import com.schediflow.dto.request.LessonMoveRequest;
import com.schediflow.dto.response.ConflictViolation;
import com.schediflow.dto.response.LessonResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.TenantContext;
import com.schediflow.websocket.WebSocketDestinations;
import com.schediflow.websocket.WebSocketEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Direct edits to a placed lesson: move (SCHED-08), pin (SCHED-09) and swap (SCHED-10).
 *
 * <p>Every mutation broadcasts {@code LESSON_UPDATED} on the timetable topic (SCHED-12) so open
 * grids stay in step.</p>
 */
@Service
public class LessonService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MODERATOR = "MODERATOR";

    private final LessonRepository lessonRepository;
    private final RoomRepository roomRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final TeacherRepository teacherRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final WebSocketEventPublisher eventPublisher;

    public LessonService(
            LessonRepository lessonRepository,
            RoomRepository roomRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            TeacherRepository teacherRepository,
            ConflictDetectionService conflictDetectionService,
            WebSocketEventPublisher eventPublisher) {
        this.lessonRepository = lessonRepository;
        this.roomRepository = roomRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.teacherRepository = teacherRepository;
        this.conflictDetectionService = conflictDetectionService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Moves a lesson to a new period and/or room (SCHED-08).
     *
     * <p>Conflicts are reported, not blocking: a moderator dragging a card may knowingly create a
     * temporary clash, so the move commits and the response carries the violations. What is blocked
     * is an invalid request — an unknown period or room, or no change at all.</p>
     */
    @Transactional
    public LessonResponse move(JwtPrincipal principal, Long lessonId, LessonMoveRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Lesson lesson = findOrThrow(tenantId, lessonId);
        assertMayEdit(principal, lesson, tenantId);

        if (req.periodId() == null && req.roomId() == null) {
            throw new BadRequestException("Supply at least one of periodId or roomId");
        }
        if (req.periodId() != null) {
            schedulePeriodRepository
                    .findByIdAndTenantId(req.periodId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Schedule period not found: " + req.periodId()));
        }
        if (req.roomId() != null) {
            roomRepository
                    .findByIdAndTenantId(req.roomId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + req.roomId()));
        }

        List<ConflictViolation> conflicts =
                conflictDetectionService.checkConflicts(lesson, req.periodId(), req.roomId());

        if (req.periodId() != null) {
            lesson.setSchedulePeriodId(req.periodId());
        }
        if (req.roomId() != null) {
            lesson.setRoomId(req.roomId());
        }
        Lesson saved = lessonRepository.save(lesson);

        publish(saved, !conflicts.isEmpty());
        return toResponse(saved, conflicts);
    }

    /** Pins or unpins a lesson so the solver leaves it alone (SCHED-09). */
    @Transactional
    public LessonResponse setPinned(JwtPrincipal principal, Long lessonId, boolean pinned) {
        Long tenantId = TenantContext.getTenantId();
        Lesson lesson = findOrThrow(tenantId, lessonId);
        assertMayEdit(principal, lesson, tenantId);

        lesson.setPinned(pinned);
        Lesson saved = lessonRepository.save(lesson);

        // Pinning does not change position, so its conflict state is whatever it already was.
        List<ConflictViolation> conflicts = conflictDetectionService.checkConflicts(saved, null, null);
        publish(saved, !conflicts.isEmpty());
        return toResponse(saved, conflicts);
    }

    /**
     * Exchanges the period and room of two lessons in one transaction (SCHED-10).
     *
     * <p>Unlike a move, a swap is rejected outright if either side would end up in conflict — a
     * swap is a convenience operation, and silently degrading two lessons at once is worse than
     * refusing.</p>
     */
    @Transactional
    public List<LessonResponse> swap(JwtPrincipal principal, Long lessonId, Long targetLessonId) {
        Long tenantId = TenantContext.getTenantId();
        if (Objects.equals(lessonId, targetLessonId)) {
            throw new BadRequestException("A lesson cannot be swapped with itself");
        }

        Lesson first = findOrThrow(tenantId, lessonId);
        Lesson second = findOrThrow(tenantId, targetLessonId);
        assertMayEdit(principal, first, tenantId);
        assertMayEdit(principal, second, tenantId);

        Long firstPeriod = first.getSchedulePeriodId();
        Long firstRoom = first.getRoomId();
        Long secondPeriod = second.getSchedulePeriodId();
        Long secondRoom = second.getRoomId();

        List<ConflictViolation> firstConflicts =
                conflictDetectionService.checkConflicts(first, secondPeriod, secondRoom);
        List<ConflictViolation> secondConflicts =
                conflictDetectionService.checkConflicts(second, firstPeriod, firstRoom);
        // Each check ignores only itself, so they would otherwise see each other in the old
        // positions; drop violations that name the other side of this very swap.
        firstConflicts = withoutCounterpart(firstConflicts, second.getId());
        secondConflicts = withoutCounterpart(secondConflicts, first.getId());

        if (!firstConflicts.isEmpty() || !secondConflicts.isEmpty()) {
            throw new BadRequestException("Swap would create conflicts and was not applied");
        }

        first.setSchedulePeriodId(secondPeriod);
        first.setRoomId(secondRoom);
        second.setSchedulePeriodId(firstPeriod);
        second.setRoomId(firstRoom);
        Lesson savedFirst = lessonRepository.save(first);
        Lesson savedSecond = lessonRepository.save(second);

        publish(savedFirst, false);
        publish(savedSecond, false);
        return List.of(toResponse(savedFirst, List.of()), toResponse(savedSecond, List.of()));
    }

    private static List<ConflictViolation> withoutCounterpart(
            List<ConflictViolation> violations, Long counterpartLessonId) {
        return violations.stream()
                .filter(v -> !Objects.equals(v.conflictingLessonId(), counterpartLessonId))
                .toList();
    }

    private Lesson findOrThrow(Long tenantId, Long lessonId) {
        return lessonRepository
                .findByIdAndTenantId(lessonId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));
    }

    /** ADMIN and MODERATOR may edit any lesson; anyone else only the lessons they teach. */
    private void assertMayEdit(JwtPrincipal principal, Lesson lesson, Long tenantId) {
        String role = principal == null ? null : principal.role();
        if (ROLE_ADMIN.equals(role) || ROLE_MODERATOR.equals(role)) {
            return;
        }
        if (principal == null || !Objects.equals(lesson.getTeacherUserId(), principal.userId())) {
            throw new AccessDeniedException("You can only change your own lessons");
        }
    }

    private void publish(Lesson lesson, boolean hasConflict) {
        Long teacherId = teacherRepository
                .findByUserIdAndTenantId(lesson.getTeacherUserId(), lesson.getTenantId())
                .map(Teacher::getId)
                .orElse(null);
        eventPublisher.publishToTopic(
                WebSocketDestinations.timetableTopic(lesson.getTimetableId()),
                new LessonUpdatedEvent(
                        lesson.getId(),
                        lesson.getTimetableId(),
                        lesson.getSchedulePeriodId(),
                        lesson.getRoomId(),
                        teacherId,
                        lesson.isPinned(),
                        hasConflict));
    }

    private static LessonResponse toResponse(Lesson lesson, List<ConflictViolation> conflicts) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getTimetableId(),
                lesson.getSubjectId(),
                lesson.getClassId(),
                lesson.getTeacherUserId(),
                lesson.getRoomId(),
                lesson.getSchedulePeriodId(),
                lesson.getScheduledDate(),
                lesson.isPinned(),
                !conflicts.isEmpty(),
                conflicts);
    }
}
