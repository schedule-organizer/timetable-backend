package com.schediflow.service;

import com.schediflow.domain.Teacher;
import com.schediflow.dto.response.TimetableLessonResponse;
import com.schediflow.dto.response.TimetableLessonRow;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The lesson grid behind a timetable view (SCHED-02).
 *
 * <p>Two queries total: one joined projection for the rows, one batched conflict pass. Neither
 * scales with the number of lessons.</p>
 */
@Service
public class TimetableGridService {

    private final TimetableRepository timetableRepository;
    private final LessonRepository lessonRepository;
    private final TeacherRepository teacherRepository;
    private final ConflictDetectionService conflictDetectionService;

    public TimetableGridService(
            TimetableRepository timetableRepository,
            LessonRepository lessonRepository,
            TeacherRepository teacherRepository,
            ConflictDetectionService conflictDetectionService) {
        this.timetableRepository = timetableRepository;
        this.lessonRepository = lessonRepository;
        this.teacherRepository = teacherRepository;
        this.conflictDetectionService = conflictDetectionService;
    }

    /**
     * @param teacherId filters by {@code teachers.id}, matching the rest of the API; it is resolved
     *                  to the user id that lessons actually carry
     */
    @Transactional(readOnly = true)
    public List<TimetableLessonResponse> getLessons(
            Long timetableId, Long teacherId, Long classId, Long roomId) {

        Long tenantId = TenantContext.getTenantId();
        timetableRepository
                .findByIdAndTenantId(timetableId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable not found: " + timetableId));

        Long teacherUserId = resolveTeacherUserId(tenantId, teacherId);
        if (teacherId != null && teacherUserId == null) {
            // A real teacher filter that matches nobody yields an empty grid, not every lesson.
            return List.of();
        }

        List<TimetableLessonRow> rows =
                lessonRepository.findGridRows(tenantId, timetableId, teacherUserId, classId, roomId);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Boolean> conflicts = conflictDetectionService.hasConflictByLessonId(tenantId, timetableId);
        return rows.stream().map(row -> toResponse(row, conflicts)).toList();
    }

    private Long resolveTeacherUserId(Long tenantId, Long teacherId) {
        if (teacherId == null) {
            return null;
        }
        return teacherRepository
                .findByIdAndTenantIdAndActive(teacherId, tenantId, true)
                .map(Teacher::getUserId)
                .orElse(null);
    }

    private static TimetableLessonResponse toResponse(TimetableLessonRow row, Map<Long, Boolean> conflicts) {
        return new TimetableLessonResponse(
                row.lessonId(),
                row.subjectName(),
                row.teacherName(),
                row.roomName(),
                row.periodId(),
                row.scheduledDate(),
                row.scheduledDate().getDayOfWeek().getValue(),
                row.isPinned(),
                conflicts.getOrDefault(row.lessonId(), false));
    }
}
