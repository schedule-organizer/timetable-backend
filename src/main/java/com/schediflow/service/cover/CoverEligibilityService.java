package com.schediflow.service.cover;

import com.schediflow.domain.ForbiddenSlot;
import com.schediflow.domain.ForbiddenSlotEntityType;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.Teacher;
import com.schediflow.repository.CoverAssignmentRepository;
import com.schediflow.repository.ForbiddenSlotRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TeacherQualificationRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared "can this teacher take this lesson?" rules, used to reject an assignment in COVER-01 and to
 * filter candidates in COVER-02, so the two can never disagree about who is eligible.
 *
 * <p>The slot facts are loaded once per lesson into a {@link SlotAvailability} snapshot — three
 * queries regardless of how many candidates are then tested against it.</p>
 */
@Service
public class CoverEligibilityService {

    private final LessonRepository lessonRepository;
    private final CoverAssignmentRepository coverAssignmentRepository;
    private final ForbiddenSlotRepository forbiddenSlotRepository;
    private final TeacherQualificationRepository teacherQualificationRepository;

    public CoverEligibilityService(
            LessonRepository lessonRepository,
            CoverAssignmentRepository coverAssignmentRepository,
            ForbiddenSlotRepository forbiddenSlotRepository,
            TeacherQualificationRepository teacherQualificationRepository) {
        this.lessonRepository = lessonRepository;
        this.coverAssignmentRepository = coverAssignmentRepository;
        this.forbiddenSlotRepository = forbiddenSlotRepository;
        this.teacherQualificationRepository = teacherQualificationRepository;
    }

    public boolean isQualified(Long tenantId, Long teacherId, Long subjectId) {
        return teacherQualificationRepository.existsByTeacherIdAndTenantIdAndSubjectId(
                teacherId, tenantId, subjectId);
    }

    /**
     * Snapshots who is unavailable in the lesson's slot: teachers already teaching then, teachers
     * already covering another lesson then, and teachers with a forbidden slot covering it.
     */
    public SlotAvailability availabilityAt(Long tenantId, Lesson lesson) {
        Set<Long> busyTeacherUserIds = new HashSet<>(lessonRepository.findTeacherUserIdsBusyAt(
                tenantId, lesson.getScheduledDate(), lesson.getSchedulePeriodId(), lesson.getId()));
        Set<Long> busyCoverTeacherIds = new HashSet<>(coverAssignmentRepository.findCoverTeacherIdsBusyAt(
                tenantId, lesson.getScheduledDate(), lesson.getSchedulePeriodId(), lesson.getId()));

        int dayOfWeek = lesson.getScheduledDate().getDayOfWeek().getValue();
        Set<Long> forbiddenTeacherIds = new HashSet<>();
        for (ForbiddenSlot slot : forbiddenSlotRepository.findByTenantIdAndEntityTypeAndSchedulePeriodId(
                tenantId, ForbiddenSlotEntityType.TEACHER.name(), lesson.getSchedulePeriodId())) {
            if (appliesOn(slot, lesson, dayOfWeek)) {
                forbiddenTeacherIds.add(slot.getEntityId());
            }
        }
        return new SlotAvailability(busyTeacherUserIds, busyCoverTeacherIds, forbiddenTeacherIds);
    }

    private static boolean appliesOn(ForbiddenSlot slot, Lesson lesson, int dayOfWeek) {
        if (slot.isRecurring()) {
            return slot.getDayOfWeek() != null && slot.getDayOfWeek() == dayOfWeek;
        }
        return lesson.getScheduledDate().equals(slot.getSpecificDate());
    }

    /**
     * Availability of one slot, resolved once and then queried per candidate.
     *
     * @param busyTeacherUserIds  {@code users.id} of teachers already teaching in the slot
     * @param busyCoverTeacherIds {@code teachers.id} of teachers already covering in the slot
     * @param forbiddenTeacherIds {@code teachers.id} of teachers with a forbidden slot there
     */
    public record SlotAvailability(
            Set<Long> busyTeacherUserIds,
            Set<Long> busyCoverTeacherIds,
            Set<Long> forbiddenTeacherIds) {

        /** True when the teacher already teaches or already covers something in this slot. */
        public boolean hasTimetableConflict(Teacher teacher) {
            return busyTeacherUserIds.contains(teacher.getUserId())
                    || busyCoverTeacherIds.contains(teacher.getId());
        }

        public boolean hasForbiddenSlot(Teacher teacher) {
            return forbiddenTeacherIds.contains(teacher.getId());
        }

        public boolean isFree(Teacher teacher) {
            return !hasTimetableConflict(teacher) && !hasForbiddenSlot(teacher);
        }
    }
}
