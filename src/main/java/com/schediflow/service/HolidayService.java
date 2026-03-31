package com.schediflow.service;

import com.schediflow.domain.HolidayDate;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.domain.Term;
import com.schediflow.domain.Timetable;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.HolidayCalendarRepository;
import com.schediflow.repository.HolidayDateRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.TermRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.security.TenantContext;
import com.schediflow.solver.model.PeriodSlot;
import com.schediflow.solver.model.UnavailablePeriodPenalty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class HolidayService {

    private final TimetableRepository timetableRepository;
    private final TermRepository termRepository;
    private final HolidayCalendarRepository holidayCalendarRepository;
    private final HolidayDateRepository holidayDateRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;

    public HolidayService(
            TimetableRepository timetableRepository,
            TermRepository termRepository,
            HolidayCalendarRepository holidayCalendarRepository,
            HolidayDateRepository holidayDateRepository,
            SchedulePeriodRepository schedulePeriodRepository) {
        this.timetableRepository = timetableRepository;
        this.termRepository = termRepository;
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.holidayDateRepository = holidayDateRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
    }

    /**
     * Returns period slots that fall on configured holiday dates within the timetable's term, using the timetable's
     * bell schedule. Used by the solver as hard constraints (see {@link UnavailablePeriodPenalty}).
     */
    @Transactional(readOnly = true)
    public List<PeriodSlot> getForbiddenSlots(Long timetableId) {
        Long tenantId = TenantContext.getTenantId();
        Timetable timetable =
                timetableRepository.findByIdAndTenantId(timetableId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Timetable not found"));
        Term term = termRepository.findByIdAndTenantId(timetable.getTermId(), tenantId).orElseThrow(() -> new ResourceNotFoundException("Term not found"));

        var calendar = holidayCalendarRepository.findByAcademicYearIdAndTenantId(term.getAcademicYearId(), tenantId);
        if (calendar.isEmpty()) {
            return List.of();
        }

        List<HolidayDate> holidayRows = holidayDateRepository.findByHolidayCalendarIdOrderByDateAsc(calendar.get().getId());
        List<SchedulePeriod> periods = schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(timetable.getBellScheduleId());
        List<SchedulePeriod> teachable = periods.stream().filter(p -> !p.isBreak()).toList();

        LocalDate termStart = term.getStartDate();
        LocalDate termEnd = term.getEndDate();

        List<PeriodSlot> result = new ArrayList<>();
        Set<LocalDate> seenHolidayDates = new HashSet<>();
        for (HolidayDate hd : holidayRows) {
            LocalDate d = hd.getDate();
            if (d.isBefore(termStart) || d.isAfter(termEnd)) {
                continue;
            }
            if (!seenHolidayDates.add(d)) {
                continue;
            }
            for (SchedulePeriod p : teachable) {
                result.add(new PeriodSlot(d, p.getId(), p.getOrdinal()));
            }
        }
        result.sort(Comparator.comparing(PeriodSlot::getDate).thenComparingInt(PeriodSlot::getOrdinal));
        return List.copyOf(result);
    }

    /**
     * Same data as {@link #getForbiddenSlots(Long)} wrapped as Timefold problem facts for hard constraints.
     */
    @Transactional(readOnly = true)
    public List<UnavailablePeriodPenalty> getHolidayPenalties(Long timetableId) {
        return getForbiddenSlots(timetableId).stream().map(UnavailablePeriodPenalty::new).toList();
    }
}
