package com.schediflow.service.solver;

import com.schediflow.domain.HolidayDate;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.TeacherQualification;
import com.schediflow.domain.Timetable;
import com.schediflow.dto.request.SolverScope;
import com.schediflow.repository.HolidayDateRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.TeacherQualificationRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.solver.model.OptionBlockMembership;
import com.schediflow.solver.model.PeriodSlot;
import com.schediflow.solver.model.TeacherSubjectQualification;
import com.schediflow.solver.model.TimetableSolution;
import com.schediflow.solver.model.UnavailablePeriodPenalty;
import com.schediflow.service.OptionBlockService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns persisted rows into a Timefold {@link TimetableSolution} (SCHED-03).
 *
 * <p>Kept separate from the run/tracking logic so problem assembly can be tested without starting a
 * solver.</p>
 */
@Component
public class SolverProblemBuilder {

    private final LessonRepository lessonRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherQualificationRepository teacherQualificationRepository;
    private final HolidayDateRepository holidayDateRepository;
    private final OptionBlockService optionBlockService;

    public SolverProblemBuilder(
            LessonRepository lessonRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            TeacherRepository teacherRepository,
            TeacherQualificationRepository teacherQualificationRepository,
            HolidayDateRepository holidayDateRepository,
            OptionBlockService optionBlockService) {
        this.lessonRepository = lessonRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.teacherRepository = teacherRepository;
        this.teacherQualificationRepository = teacherQualificationRepository;
        this.holidayDateRepository = holidayDateRepository;
        this.optionBlockService = optionBlockService;
    }

    @Transactional(readOnly = true)
    public TimetableSolution build(Timetable timetable) {
        return build(timetable, null);
    }

    /**
     * @param scope when non-null, only lessons it matches may move; everything else is pinned so a
     *              targeted run cannot silently disturb the rest of the timetable (SCHED-14)
     */
    @Transactional(readOnly = true)
    public TimetableSolution build(Timetable timetable, SolverScope scope) {
        Long tenantId = timetable.getTenantId();
        List<com.schediflow.domain.Lesson> persisted =
                lessonRepository.findByTenantIdAndTimetableIdOrderByScheduledDateAscSchedulePeriodIdAsc(
                        tenantId, timetable.getId());

        // Teaching periods of this timetable's bell schedule; break and lunch are never placeable.
        List<SchedulePeriod> periods =
                schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(timetable.getBellScheduleId())
                        .stream()
                        .filter(p -> !p.isBreak() && !p.isLunch())
                        .toList();

        // The solver rearranges existing lessons, so the placeable dates are the ones they already
        // occupy. Generating lessons for an empty timetable is a separate concern (see the story).
        Set<LocalDate> dates = new TreeSet<>(
                persisted.stream().map(com.schediflow.domain.Lesson::getScheduledDate).toList());

        List<PeriodSlot> slots = new ArrayList<>(dates.size() * periods.size());
        for (LocalDate date : dates) {
            for (SchedulePeriod period : periods) {
                slots.add(new PeriodSlot(date, period.getId(), period.getOrdinal()));
            }
        }

        Map<Long, PeriodSlot> slotByKey = slots.stream()
                .collect(Collectors.toMap(
                        slot -> slotKey(slot.getDate(), slot.getSchedulePeriodId()),
                        Function.identity(),
                        (a, b) -> a));

        List<com.schediflow.solver.model.Lesson> planningLessons = new ArrayList<>(persisted.size());
        for (com.schediflow.domain.Lesson row : persisted) {
            com.schediflow.solver.model.Lesson lesson = new com.schediflow.solver.model.Lesson();
            lesson.setId(row.getId());
            lesson.setTeacherUserId(row.getTeacherUserId());
            lesson.setSubjectId(row.getSubjectId());
            // A lesson is frozen if it was pinned by hand, or if a scope excludes it.
            lesson.setPinned(row.isPinned() || !inScope(scope, row));
            lesson.setPeriodSlot(
                    slotByKey.get(slotKey(row.getScheduledDate(), row.getSchedulePeriodId())));
            planningLessons.add(lesson);
        }

        TimetableSolution solution = new TimetableSolution();
        solution.setLessons(planningLessons);
        solution.setTimeslotRange(slots);
        solution.setHolidayPenalties(holidayPenalties(tenantId, dates, periods));
        solution.setTeacherSubjectQualifications(qualifications(tenantId));
        solution.setOptionBlockMemberships(optionBlockService.membershipFacts(tenantId));
        return solution;
    }

    /** Every slot on a holiday date is forbidden, which is what HOL-05's hard constraint reads. */
    private List<UnavailablePeriodPenalty> holidayPenalties(
            Long tenantId, Set<LocalDate> dates, List<SchedulePeriod> periods) {
        if (dates.isEmpty() || periods.isEmpty()) {
            return List.of();
        }
        LocalDate from = dates.iterator().next();
        LocalDate to = dates.stream().max(Comparator.naturalOrder()).orElse(from);

        Set<LocalDate> holidays = holidayDateRepository.findByTenantIdAndDateBetween(tenantId, from, to)
                .stream()
                .map(HolidayDate::getDate)
                .collect(Collectors.toSet());

        List<UnavailablePeriodPenalty> penalties = new ArrayList<>();
        for (LocalDate date : dates) {
            if (!holidays.contains(date)) {
                continue;
            }
            for (SchedulePeriod period : periods) {
                penalties.add(new UnavailablePeriodPenalty(
                        new PeriodSlot(date, period.getId(), period.getOrdinal())));
            }
        }
        return penalties;
    }

    /** Qualifications are stored against a teacher profile; the solver matches on the user id. */
    private List<TeacherSubjectQualification> qualifications(Long tenantId) {
        Map<Long, Long> userIdByTeacherId =
                teacherRepository.findByTenantIdAndActiveOrderByDisplayNameAsc(tenantId, true).stream()
                        .collect(Collectors.toMap(Teacher::getId, Teacher::getUserId, (a, b) -> a));

        List<TeacherSubjectQualification> facts = new ArrayList<>();
        for (TeacherQualification qualification : teacherQualificationRepository.findByTenantId(tenantId)) {
            Long userId = userIdByTeacherId.get(qualification.getTeacherId());
            if (userId != null) {
                facts.add(new TeacherSubjectQualification(userId, qualification.getSubjectId()));
            }
        }
        return facts;
    }

    /** No scope means everything is movable; otherwise any matching dimension puts a lesson in scope. */
    public static boolean inScope(SolverScope scope, com.schediflow.domain.Lesson lesson) {
        if (scope == null || scope.isEmpty()) {
            return true;
        }
        return contains(scope.teacherIds(), lesson.getTeacherUserId())
                || contains(scope.classIds(), lesson.getClassId())
                || contains(scope.roomIds(), lesson.getRoomId());
    }

    private static boolean contains(List<Long> ids, Long value) {
        return ids != null && value != null && ids.contains(value);
    }

    private static long slotKey(LocalDate date, Long periodId) {
        return date.toEpochDay() * 1_000_003L + periodId;
    }
}
