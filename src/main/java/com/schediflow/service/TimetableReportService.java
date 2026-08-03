package com.schediflow.service;

import com.schediflow.domain.ClassSubjectHour;
import com.schediflow.domain.Room;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.domain.SchoolClass;
import com.schediflow.domain.Subject;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.Timetable;
import com.schediflow.dto.response.RoomUtilizationReport;
import com.schediflow.dto.response.SubjectCoverageReport;
import com.schediflow.dto.response.TeacherUtilizationReport;
import com.schediflow.dto.response.TimetableExportRow;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.ClassSubjectHourRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.SubjectRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only aggregations over a timetable (EXPORT-05, EXPORT-06, EXPORT-07).
 *
 * <p>All three load the same joined export rows once and aggregate in memory, so a report costs a
 * fixed handful of queries regardless of how many lessons the timetable holds.</p>
 */
@Service
public class TimetableReportService {

    private static final double OVERLOADED_PCT = 100.0;
    private static final double UNDERUTILIZED_PCT = 70.0;

    private final TimetableRepository timetableRepository;
    private final LessonRepository lessonRepository;
    private final TeacherRepository teacherRepository;
    private final RoomRepository roomRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectHourRepository classSubjectHourRepository;

    public TimetableReportService(
            TimetableRepository timetableRepository,
            LessonRepository lessonRepository,
            TeacherRepository teacherRepository,
            RoomRepository roomRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            SchoolClassRepository schoolClassRepository,
            SubjectRepository subjectRepository,
            ClassSubjectHourRepository classSubjectHourRepository) {
        this.timetableRepository = timetableRepository;
        this.lessonRepository = lessonRepository;
        this.teacherRepository = teacherRepository;
        this.roomRepository = roomRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.subjectRepository = subjectRepository;
        this.classSubjectHourRepository = classSubjectHourRepository;
    }

    // ---------- EXPORT-05 ----------

    @Transactional(readOnly = true)
    public TeacherUtilizationReport teacherUtilization(Long timetableId) {
        Long tenantId = TenantContext.getTenantId();
        findTimetableOrThrow(tenantId, timetableId);
        List<TimetableExportRow> rows = lessonRepository.findExportRows(tenantId, timetableId);

        Map<Long, List<TimetableExportRow>> byTeacherUser =
                rows.stream().collect(Collectors.groupingBy(TimetableExportRow::teacherUserId));

        List<TeacherUtilizationReport.TeacherUtilizationRow> result = new ArrayList<>();
        for (Teacher teacher :
                teacherRepository.findByTenantIdAndActiveOrderByDisplayNameAsc(tenantId, true)) {
            List<TimetableExportRow> theirs =
                    byTeacherUser.getOrDefault(teacher.getUserId(), List.of());
            Integer cap = teacher.getWorkloadCap();
            Double utilization = (cap == null || cap.intValue() == 0)
                    ? null
                    : round(theirs.size() * 100.0 / cap);

            result.add(new TeacherUtilizationReport.TeacherUtilizationRow(
                    teacher.getId(),
                    teacher.getDisplayName(),
                    theirs.size(),
                    cap,
                    utilization,
                    countGaps(theirs),
                    subjectDistribution(theirs)));
        }

        // Most loaded first; uncapped teachers have no percentage, so they sort last.
        result.sort(Comparator.comparing(
                        TeacherUtilizationReport.TeacherUtilizationRow::utilizationPct,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(TeacherUtilizationReport.TeacherUtilizationRow::displayName));

        List<Double> known = result.stream()
                .map(TeacherUtilizationReport.TeacherUtilizationRow::utilizationPct)
                .filter(Objects::nonNull)
                .toList();
        Double avg = known.isEmpty()
                ? null
                : round(known.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        int overloaded = (int) known.stream().filter(p -> p > OVERLOADED_PCT).count();
        int under = (int) known.stream().filter(p -> p < UNDERUTILIZED_PCT).count();

        return new TeacherUtilizationReport(
                result, new TeacherUtilizationReport.Summary(avg, overloaded, under));
    }

    /** Free teaching periods between a teacher's first and last lesson on each day. */
    private static int countGaps(List<TimetableExportRow> lessons) {
        Map<LocalDate, List<Integer>> ordinalsByDay = lessons.stream()
                .collect(Collectors.groupingBy(
                        TimetableExportRow::scheduledDate,
                        Collectors.mapping(TimetableExportRow::periodOrdinal, Collectors.toList())));

        int gaps = 0;
        for (List<Integer> ordinals : ordinalsByDay.values()) {
            Set<Integer> occupied = new TreeSet<>(ordinals);
            int first = occupied.iterator().next();
            int last = occupied.stream().max(Integer::compareTo).orElse(first);
            for (int ordinal = first; ordinal <= last; ordinal++) {
                if (!occupied.contains(ordinal)) {
                    gaps++;
                }
            }
        }
        return gaps;
    }

    private static List<TeacherUtilizationReport.SubjectCount> subjectDistribution(
            List<TimetableExportRow> lessons) {
        return lessons.stream()
                .collect(Collectors.groupingBy(TimetableExportRow::subjectName, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new TeacherUtilizationReport.SubjectCount(e.getKey(), e.getValue().intValue()))
                .toList();
    }

    // ---------- EXPORT-06 ----------

    @Transactional(readOnly = true)
    public RoomUtilizationReport roomUtilization(Long timetableId) {
        Long tenantId = TenantContext.getTenantId();
        Timetable timetable = findTimetableOrThrow(tenantId, timetableId);
        List<TimetableExportRow> rows = lessonRepository.findExportRows(tenantId, timetableId);

        List<SchedulePeriod> periods =
                schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(timetable.getBellScheduleId())
                        .stream()
                        .filter(p -> !p.isBreak() && !p.isLunch())
                        .toList();
        // The cycle is the distinct days the timetable actually spans.
        long days = rows.stream().map(TimetableExportRow::scheduledDate).distinct().count();
        int totalPeriods = (int) (days * periods.size());

        Map<Long, List<TimetableExportRow>> byRoom = rows.stream()
                .filter(r -> r.roomId() != null)
                .collect(Collectors.groupingBy(TimetableExportRow::roomId));

        List<RoomUtilizationReport.RoomUtilizationRow> result = new ArrayList<>();
        for (Room room : roomRepository.findByTenantIdAndActiveOrderByNameAsc(tenantId, true)) {
            List<TimetableExportRow> theirs = byRoom.getOrDefault(room.getId(), List.of());
            Map<String, Long> byPeriodName = theirs.stream()
                    .collect(Collectors.groupingBy(TimetableExportRow::periodName, Collectors.counting()));

            Map<String, Double> occupancyByPeriod = new LinkedHashMap<>();
            for (SchedulePeriod period : periods) {
                long used = byPeriodName.getOrDefault(period.getName(), 0L);
                occupancyByPeriod.put(
                        period.getName(), days == 0 ? 0.0 : round(used * 100.0 / days));
            }
            double avg = totalPeriods == 0 ? 0.0 : round(theirs.size() * 100.0 / totalPeriods);
            result.add(new RoomUtilizationReport.RoomUtilizationRow(
                    room.getId(), room.getName(), room.getType(), occupancyByPeriod, avg));
        }

        Map<String, Double> byType = result.stream()
                .collect(Collectors.groupingBy(
                        RoomUtilizationReport.RoomUtilizationRow::roomType,
                        Collectors.averagingDouble(
                                RoomUtilizationReport.RoomUtilizationRow::avgOccupancy)));
        byType.replaceAll((type, value) -> round(value));

        result.sort(Comparator.comparingDouble(
                RoomUtilizationReport.RoomUtilizationRow::avgOccupancy).reversed());
        return new RoomUtilizationReport(result, byType, totalPeriods);
    }

    // ---------- EXPORT-07 ----------

    @Transactional(readOnly = true)
    public SubjectCoverageReport subjectCoverage(Long timetableId) {
        Long tenantId = TenantContext.getTenantId();
        findTimetableOrThrow(tenantId, timetableId);
        List<TimetableExportRow> rows = lessonRepository.findExportRows(tenantId, timetableId);

        Map<String, Long> actualByClassSubject = rows.stream()
                .collect(Collectors.groupingBy(
                        r -> r.classId() + "|" + r.subjectName(), Collectors.counting()));

        Map<Long, String> classNames =
                schoolClassRepository.findByTenantIdAndActiveOrderByNameAsc(tenantId, true).stream()
                        .collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getName, (a, b) -> a));
        Map<Long, String> subjectNames =
                subjectRepository.findByTenantIdAndActiveOrderByNameAsc(tenantId, true).stream()
                        .collect(Collectors.toMap(Subject::getId, Subject::getName, (a, b) -> a));

        List<SubjectCoverageReport.CoverageRow> coverage = new ArrayList<>();
        int under = 0;
        int over = 0;
        int onTarget = 0;

        for (Long classId : classNames.keySet().stream().sorted().toList()) {
            for (ClassSubjectHour requirement :
                    classSubjectHourRepository.findByTenantIdAndClassIdOrderBySubjectIdAsc(tenantId, classId)) {
                String subjectName = subjectNames.get(requirement.getSubjectId());
                if (subjectName == null) {
                    continue;
                }
                int required = requirement.getPeriodsPerCycle();
                int actual = actualByClassSubject
                        .getOrDefault(classId + "|" + subjectName, 0L).intValue();
                int variance = actual - required;
                String status = variance == 0 ? "ON_TARGET" : (variance < 0 ? "UNDER" : "OVER");
                if (variance == 0) {
                    onTarget++;
                } else if (variance < 0) {
                    under++;
                } else {
                    over++;
                }
                coverage.add(new SubjectCoverageReport.CoverageRow(
                        classId, classNames.get(classId), requirement.getSubjectId(), subjectName,
                        required, actual, variance, status));
            }
        }

        return new SubjectCoverageReport(
                coverage, new SubjectCoverageReport.Summary(under, over, onTarget));
    }

    private Timetable findTimetableOrThrow(Long tenantId, Long timetableId) {
        return timetableRepository
                .findByIdAndTenantId(timetableId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable not found: " + timetableId));
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
