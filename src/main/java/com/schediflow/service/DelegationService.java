package com.schediflow.service;

import com.schediflow.domain.DelegationRequest;
import com.schediflow.domain.DelegationRequestLesson;
import com.schediflow.domain.DelegationStatus;
import com.schediflow.domain.DelegationType;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.Teacher;
import com.schediflow.dto.request.DelegationRequestSubmission;
import com.schediflow.dto.response.DelegationRequestResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.DelegationRequestLessonRepository;
import com.schediflow.repository.DelegationRequestRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Teacher-initiated delegation of lessons (COVER-03). Decisions are handled in COVER-04.
 */
@Service
public class DelegationService {

    private final DelegationRequestRepository delegationRequestRepository;
    private final DelegationRequestLessonRepository delegationRequestLessonRepository;
    private final LessonRepository lessonRepository;
    private final TeacherRepository teacherRepository;

    public DelegationService(
            DelegationRequestRepository delegationRequestRepository,
            DelegationRequestLessonRepository delegationRequestLessonRepository,
            LessonRepository lessonRepository,
            TeacherRepository teacherRepository) {
        this.delegationRequestRepository = delegationRequestRepository;
        this.delegationRequestLessonRepository = delegationRequestLessonRepository;
        this.lessonRepository = lessonRepository;
        this.teacherRepository = teacherRepository;
    }

    @Transactional
    public DelegationRequestResponse submit(JwtPrincipal principal, DelegationRequestSubmission req) {
        Long tenantId = TenantContext.getTenantId();
        DelegationType type = parseType(req.type());

        List<Long> lessonIds = new ArrayList<>(new LinkedHashSet<>(req.lessonIds()));
        if (lessonIds.size() != req.lessonIds().size()) {
            throw new BadRequestException("Duplicate lesson id in lessonIds");
        }
        if (lessonIds.contains(null)) {
            throw new BadRequestException("lessonIds must not contain null");
        }

        Teacher target = teacherRepository
                .findByIdAndTenantIdAndActive(req.targetTeacherId(), tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + req.targetTeacherId()));
        if (Objects.equals(target.getUserId(), principal.userId())) {
            throw new BadRequestException("Cannot delegate lessons to yourself");
        }

        List<Lesson> lessons = lessonRepository.findByIdInAndTenantId(lessonIds, tenantId);
        Map<Long, Lesson> byId = lessons.stream().collect(Collectors.toMap(Lesson::getId, Function.identity()));
        for (Long lessonId : lessonIds) {
            Lesson lesson = byId.get(lessonId);
            if (lesson == null) {
                throw new ResourceNotFoundException("Lesson not found: " + lessonId);
            }
            // A teacher may only delegate lessons that are actually theirs.
            if (!Objects.equals(lesson.getTeacherUserId(), principal.userId())) {
                throw new AccessDeniedException("You can only delegate your own lessons");
            }
        }

        List<Long> alreadyPending =
                delegationRequestLessonRepository.findLessonIdsInPendingRequests(tenantId, lessonIds);
        if (!alreadyPending.isEmpty()) {
            throw new ConflictException(
                    "Lesson already has a pending delegation request: " + alreadyPending.get(0));
        }

        DelegationRequest request = new DelegationRequest();
        request.setTenantId(tenantId);
        request.setType(type.name());
        request.setStatus(DelegationStatus.PENDING.name());
        request.setRequestedByUserId(principal.userId());
        request.setTargetTeacherId(target.getId());
        request.setReason(trimToNull(req.reason()));
        DelegationRequest saved = delegationRequestRepository.save(request);

        for (Long lessonId : lessonIds) {
            DelegationRequestLesson link = new DelegationRequestLesson();
            link.setTenantId(tenantId);
            link.setDelegationRequestId(saved.getId());
            link.setLessonId(lessonId);
            delegationRequestLessonRepository.save(link);
        }

        return toResponse(saved, lessonIds);
    }

    static DelegationType parseType(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase();
        for (DelegationType type : DelegationType.values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        throw new BadRequestException("Invalid delegation type: " + raw + ". Must be one of: SWAP, HANDOVER");
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    List<Long> lessonIdsOf(Long delegationRequestId) {
        return delegationRequestLessonRepository
                .findByDelegationRequestIdOrderByLessonIdAsc(delegationRequestId)
                .stream()
                .map(DelegationRequestLesson::getLessonId)
                .toList();
    }

    static DelegationRequestResponse toResponse(DelegationRequest request, List<Long> lessonIds) {
        return new DelegationRequestResponse(
                request.getId(),
                request.getType(),
                request.getStatus(),
                request.getRequestedByUserId(),
                request.getTargetTeacherId(),
                lessonIds,
                request.getReason(),
                request.getRejectionReason(),
                request.getDecidedBy(),
                request.getDecidedAt(),
                request.getCreatedAt());
    }
}
