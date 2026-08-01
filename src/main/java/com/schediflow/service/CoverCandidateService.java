package com.schediflow.service;

import com.schediflow.domain.Lesson;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.TeacherQualification;
import com.schediflow.dto.response.CoverCandidateResponse;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.CoverAssignmentRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TeacherQualificationRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.security.TenantContext;
import com.schediflow.service.cover.CoverEligibilityService;
import com.schediflow.service.cover.CoverEligibilityService.SlotAvailability;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Suggests who could cover a lesson (COVER-02): teachers qualified for its subject, minus anyone
 * with a clash or a forbidden slot in that period, ordered by how much capacity they have left.
 */
@Service
public class CoverCandidateService {

    private final LessonRepository lessonRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherQualificationRepository teacherQualificationRepository;
    private final CoverAssignmentRepository coverAssignmentRepository;
    private final CoverEligibilityService coverEligibilityService;

    public CoverCandidateService(
            LessonRepository lessonRepository,
            TeacherRepository teacherRepository,
            TeacherQualificationRepository teacherQualificationRepository,
            CoverAssignmentRepository coverAssignmentRepository,
            CoverEligibilityService coverEligibilityService) {
        this.lessonRepository = lessonRepository;
        this.teacherRepository = teacherRepository;
        this.teacherQualificationRepository = teacherQualificationRepository;
        this.coverAssignmentRepository = coverAssignmentRepository;
        this.coverEligibilityService = coverEligibilityService;
    }

    public List<CoverCandidateResponse> findCandidates(Long lessonId) {
        Long tenantId = TenantContext.getTenantId();
        Lesson lesson = lessonRepository
                .findByIdAndTenantId(lessonId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));

        List<Long> qualifiedTeacherIds =
                teacherQualificationRepository
                        .findByTenantIdAndSubjectId(tenantId, lesson.getSubjectId())
                        .stream()
                        .map(TeacherQualification::getTeacherId)
                        .distinct()
                        .toList();
        if (qualifiedTeacherIds.isEmpty()) {
            return List.of();
        }

        List<Teacher> candidates =
                teacherRepository.findByTenantIdAndActiveOrderByDisplayNameAsc(tenantId, true).stream()
                        .filter(t -> qualifiedTeacherIds.contains(t.getId()))
                        // the lesson's own teacher is who we are replacing
                        .filter(t -> !Objects.equals(t.getUserId(), lesson.getTeacherUserId()))
                        .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        SlotAvailability availability = coverEligibilityService.availabilityAt(tenantId, lesson);
        List<Teacher> available = candidates.stream().filter(availability::isFree).toList();
        if (available.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> workloadByTeacherId = workload(tenantId, lesson, available);
        Map<Long, List<Long>> qualificationsByTeacherId = qualifications(tenantId, available);

        List<CoverCandidateResponse> responses = new ArrayList<>(available.size());
        for (Teacher teacher : available) {
            int currentWorkload = workloadByTeacherId.getOrDefault(teacher.getId(), 0);
            Integer cap = teacher.getWorkloadCap();
            Integer gap = cap == null ? null : cap - currentWorkload;
            responses.add(new CoverCandidateResponse(
                    teacher.getId(),
                    teacher.getDisplayName(),
                    qualificationsByTeacherId.getOrDefault(teacher.getId(), List.of()),
                    currentWorkload,
                    cap,
                    gap));
        }

        // Most spare capacity first. An uncapped teacher has no ceiling, so they sort ahead of
        // everyone; ties fall back to display name for a stable order.
        responses.sort(Comparator
                .comparing(
                        CoverCandidateResponse::workloadGap,
                        Comparator.nullsFirst(Comparator.reverseOrder()))
                .thenComparing(CoverCandidateResponse::displayName));
        return responses;
    }

    /** Lessons already taught plus lessons already covered, within the lesson's own timetable. */
    private Map<Long, Integer> workload(Long tenantId, Lesson lesson, List<Teacher> teachers) {
        Map<Long, Integer> taughtByUserId = toCountMap(
                lessonRepository.countPerTeacherUserInTimetable(tenantId, lesson.getTimetableId()));
        Map<Long, Integer> coveredByTeacherId = toCountMap(
                coverAssignmentRepository.countPerCoverTeacherInTimetable(tenantId, lesson.getTimetableId()));

        Map<Long, Integer> workload = new HashMap<>();
        for (Teacher teacher : teachers) {
            workload.put(
                    teacher.getId(),
                    taughtByUserId.getOrDefault(teacher.getUserId(), 0)
                            + coveredByTeacherId.getOrDefault(teacher.getId(), 0));
        }
        return workload;
    }

    private Map<Long, List<Long>> qualifications(Long tenantId, List<Teacher> teachers) {
        List<Long> teacherIds = teachers.stream().map(Teacher::getId).toList();
        return teacherQualificationRepository.findByTenantIdAndTeacherIdIn(tenantId, teacherIds).stream()
                .collect(Collectors.groupingBy(
                        TeacherQualification::getTeacherId,
                        Collectors.mapping(TeacherQualification::getSubjectId, Collectors.toList())));
    }

    private static Map<Long, Integer> toCountMap(List<Object[]> rows) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                counts.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
            }
        }
        return counts;
    }
}
