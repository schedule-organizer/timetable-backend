package com.schediflow.service.delegation;

import com.schediflow.domain.DelegationType;
import com.schediflow.domain.Lesson;
import com.schediflow.exception.ConflictException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Works out which lessons change hands when a delegation request is approved, and refuses the plan
 * if applying it would double-book anybody.
 *
 * <p>Planning is separated from persistence so the conflict check runs against the complete
 * post-change picture before a single row is written (COVER-04).</p>
 */
public final class DelegationReassignmentPlanner {

    private DelegationReassignmentPlanner() {}

    /** One lesson moving from its current teacher to another. */
    public record Reassignment(Lesson lesson, Long newTeacherUserId) {}

    /**
     * @param requestedLessons  the lessons named by the request (all taught by the requester)
     * @param targetLessons     every lesson currently taught by the target teacher
     * @param requesterUserId   the requesting teacher's user id
     * @param targetUserId      the target teacher's user id
     */
    public static List<Reassignment> plan(
            DelegationType type,
            List<Lesson> requestedLessons,
            List<Lesson> targetLessons,
            Long requesterUserId,
            Long targetUserId) {

        List<Reassignment> plan = new ArrayList<>();
        for (Lesson lesson : requestedLessons) {
            plan.add(new Reassignment(lesson, targetUserId));
        }

        if (type == DelegationType.SWAP) {
            // A swap gives the requester whatever the target was teaching in the same slot. Where the
            // target has nothing there, the lesson simply moves across — a swap with an empty slot.
            Map<Slot, Lesson> targetBySlot = new HashMap<>();
            for (Lesson lesson : targetLessons) {
                targetBySlot.putIfAbsent(Slot.of(lesson), lesson);
            }
            Set<Long> alreadyPlanned = new HashSet<>();
            for (Lesson lesson : requestedLessons) {
                Lesson counterpart = targetBySlot.get(Slot.of(lesson));
                if (counterpart != null && alreadyPlanned.add(counterpart.getId())) {
                    plan.add(new Reassignment(counterpart, requesterUserId));
                }
            }
        }
        return plan;
    }

    /**
     * Rejects a plan that would leave any affected teacher with two lessons in one slot.
     *
     * @param lessonsByTeacherUserId every lesson currently held by each teacher the plan touches
     */
    public static void assertNoConflicts(
            List<Reassignment> plan, Map<Long, List<Lesson>> lessonsByTeacherUserId) {

        Map<Long, Long> newOwnerByLessonId = new HashMap<>();
        for (Reassignment reassignment : plan) {
            newOwnerByLessonId.put(reassignment.lesson().getId(), reassignment.newTeacherUserId());
        }

        Map<Long, Set<Slot>> slotsByTeacher = new HashMap<>();
        for (Map.Entry<Long, List<Lesson>> entry : lessonsByTeacherUserId.entrySet()) {
            Long teacherUserId = entry.getKey();
            Set<Slot> slots = slotsByTeacher.computeIfAbsent(teacherUserId, k -> new HashSet<>());
            for (Lesson lesson : entry.getValue()) {
                Long newOwner = newOwnerByLessonId.get(lesson.getId());
                // lessons moving away no longer occupy this teacher's slot
                if (newOwner != null && !Objects.equals(newOwner, teacherUserId)) {
                    continue;
                }
                add(slots, lesson, teacherUserId);
            }
        }

        for (Reassignment reassignment : plan) {
            Long newOwner = reassignment.newTeacherUserId();
            Set<Slot> slots = slotsByTeacher.computeIfAbsent(newOwner, k -> new HashSet<>());
            add(slots, reassignment.lesson(), newOwner);
        }
    }

    private static void add(Set<Slot> slots, Lesson lesson, Long teacherUserId) {
        if (!slots.add(Slot.of(lesson))) {
            throw new ConflictException(
                    "Approving this request would double-book teacher " + teacherUserId
                            + " on " + lesson.getScheduledDate()
                            + " period " + lesson.getSchedulePeriodId());
        }
    }

    /** A concrete date and bell-schedule period — the unit a teacher can only occupy once. */
    private record Slot(LocalDate date, Long schedulePeriodId) {

        static Slot of(Lesson lesson) {
            return new Slot(lesson.getScheduledDate(), lesson.getSchedulePeriodId());
        }
    }
}
