package com.schediflow.service;

import com.schediflow.domain.CoverAssignment;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.Teacher;
import com.schediflow.dto.event.CoverAssignedEvent;
import com.schediflow.dto.request.CoverAssignmentRequest;
import com.schediflow.dto.response.CoverAssignmentResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoverAssignmentService {

    private final CoverAssignmentRepository coverAssignmentRepository;
    private final LessonRepository lessonRepository;
    private final TeacherRepository teacherRepository;
    private final CoverEligibilityService coverEligibilityService;
    private final WebSocketEventPublisher eventPublisher;

    public CoverAssignmentService(
            CoverAssignmentRepository coverAssignmentRepository,
            LessonRepository lessonRepository,
            TeacherRepository teacherRepository,
            CoverEligibilityService coverEligibilityService,
            WebSocketEventPublisher eventPublisher) {
        this.coverAssignmentRepository = coverAssignmentRepository;
        this.lessonRepository = lessonRepository;
        this.teacherRepository = teacherRepository;
        this.coverEligibilityService = coverEligibilityService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CoverAssignmentResponse assign(JwtPrincipal principal, CoverAssignmentRequest req) {
        Long tenantId = TenantContext.getTenantId();

        Lesson lesson = lessonRepository
                .findByIdAndTenantId(req.lessonId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + req.lessonId()));
        Teacher coverTeacher = teacherRepository
                .findByIdAndTenantIdAndActive(req.coverTeacherId(), tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + req.coverTeacherId()));

        if (coverAssignmentRepository.existsByLessonIdAndTenantId(lesson.getId(), tenantId)) {
            throw new ConflictException("This lesson already has a cover teacher assigned");
        }
        if (coverTeacher.getUserId().equals(lesson.getTeacherUserId())) {
            throw new BadRequestException("The lesson's own teacher cannot be assigned as cover");
        }
        if (!coverEligibilityService.isQualified(tenantId, coverTeacher.getId(), lesson.getSubjectId())) {
            throw new BadRequestException(
                    "Cover teacher is not qualified for subject " + lesson.getSubjectId());
        }

        SlotAvailability availability = coverEligibilityService.availabilityAt(tenantId, lesson);
        if (availability.hasTimetableConflict(coverTeacher)) {
            throw new ConflictException("Cover teacher is already scheduled in that period");
        }
        if (availability.hasForbiddenSlot(coverTeacher)) {
            throw new ConflictException("Cover teacher has a forbidden slot in that period");
        }

        CoverAssignment assignment = new CoverAssignment();
        assignment.setTenantId(tenantId);
        assignment.setLessonId(lesson.getId());
        assignment.setCoverTeacherId(coverTeacher.getId());
        // The lesson keeps its own teacher — cover is an overlay, not a reassignment.
        assignment.setOriginalTeacherUserId(lesson.getTeacherUserId());
        assignment.setReason(trimToNull(req.reason()));
        assignment.setAssignedBy(principal.userId());
        CoverAssignment saved = coverAssignmentRepository.save(assignment);

        eventPublisher.publishToTenant(
                tenantId,
                new CoverAssignedEvent(
                        saved.getLessonId(),
                        saved.getCoverTeacherId(),
                        saved.getOriginalTeacherUserId(),
                        saved.getAssignedAt()));

        return toResponse(saved);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static CoverAssignmentResponse toResponse(CoverAssignment assignment) {
        return new CoverAssignmentResponse(
                assignment.getId(),
                assignment.getLessonId(),
                assignment.getCoverTeacherId(),
                assignment.getOriginalTeacherUserId(),
                assignment.getReason(),
                assignment.getAssignedBy(),
                assignment.getAssignedAt());
    }
}
