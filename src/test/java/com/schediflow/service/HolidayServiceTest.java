package com.schediflow.service;

import com.schediflow.domain.HolidayCalendar;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long TIMETABLE_ID = 50L;
    private static final Long TERM_ID = 5L;
    private static final Long BELL_ID = 7L;
    private static final Long CALENDAR_ID = 20L;
    private static final Long ACADEMIC_YEAR_ID = 2L;

    @Mock TimetableRepository timetableRepository;
    @Mock TermRepository termRepository;
    @Mock HolidayCalendarRepository holidayCalendarRepository;
    @Mock HolidayDateRepository holidayDateRepository;
    @Mock SchedulePeriodRepository schedulePeriodRepository;

    HolidayService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new HolidayService(
                timetableRepository,
                termRepository,
                holidayCalendarRepository,
                holidayDateRepository,
                schedulePeriodRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getForbiddenSlots_expandsEachHolidayToTeachablePeriods() {
        Timetable tt = new Timetable();
        tt.setTenantId(TENANT_ID);
        tt.setTermId(TERM_ID);
        tt.setBellScheduleId(BELL_ID);
        when(timetableRepository.findByIdAndTenantId(TIMETABLE_ID, TENANT_ID)).thenReturn(Optional.of(tt));

        Term term = new Term();
        term.setAcademicYearId(ACADEMIC_YEAR_ID);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 12, 31));
        when(termRepository.findByIdAndTenantId(TERM_ID, TENANT_ID)).thenReturn(Optional.of(term));

        HolidayCalendar cal = new HolidayCalendar();
        setField(cal, "id", CALENDAR_ID);
        when(holidayCalendarRepository.findByAcademicYearIdAndTenantId(ACADEMIC_YEAR_ID, TENANT_ID))
                .thenReturn(Optional.of(cal));

        HolidayDate hd = new HolidayDate();
        hd.setDate(LocalDate.of(2026, 6, 1));
        when(holidayDateRepository.findByHolidayCalendarIdOrderByDateAsc(CALENDAR_ID)).thenReturn(List.of(hd));

        SchedulePeriod p1 = period(100L, 1, false);
        SchedulePeriod pBreak = period(101L, 2, true);
        SchedulePeriod p3 = period(102L, 3, false);
        when(schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(BELL_ID)).thenReturn(List.of(p1, pBreak, p3));

        List<PeriodSlot> slots = service.getForbiddenSlots(TIMETABLE_ID);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).getDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(slots.get(0).getSchedulePeriodId()).isEqualTo(100L);
        assertThat(slots.get(1).getSchedulePeriodId()).isEqualTo(102L);
    }

    @Test
    void getForbiddenSlots_duplicateRowsForSameDate_expandOncePerDate() {
        Timetable tt = new Timetable();
        tt.setTenantId(TENANT_ID);
        tt.setTermId(TERM_ID);
        tt.setBellScheduleId(BELL_ID);
        when(timetableRepository.findByIdAndTenantId(TIMETABLE_ID, TENANT_ID)).thenReturn(Optional.of(tt));

        Term term = new Term();
        term.setAcademicYearId(ACADEMIC_YEAR_ID);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 12, 31));
        when(termRepository.findByIdAndTenantId(TERM_ID, TENANT_ID)).thenReturn(Optional.of(term));

        HolidayCalendar cal = new HolidayCalendar();
        setField(cal, "id", CALENDAR_ID);
        when(holidayCalendarRepository.findByAcademicYearIdAndTenantId(ACADEMIC_YEAR_ID, TENANT_ID))
                .thenReturn(Optional.of(cal));

        LocalDate dup = LocalDate.of(2026, 6, 1);
        HolidayDate hd1 = new HolidayDate();
        hd1.setDate(dup);
        HolidayDate hd2 = new HolidayDate();
        hd2.setDate(dup);
        when(holidayDateRepository.findByHolidayCalendarIdOrderByDateAsc(CALENDAR_ID)).thenReturn(List.of(hd1, hd2));

        SchedulePeriod p1 = period(100L, 1, false);
        SchedulePeriod p3 = period(102L, 3, false);
        when(schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(BELL_ID)).thenReturn(List.of(p1, p3));

        List<PeriodSlot> slots = service.getForbiddenSlots(TIMETABLE_ID);

        assertThat(slots).hasSize(2);
    }

    @Test
    void getForbiddenSlots_noCalendar_returnsEmpty() {
        Timetable tt = new Timetable();
        tt.setTermId(TERM_ID);
        tt.setBellScheduleId(BELL_ID);
        when(timetableRepository.findByIdAndTenantId(TIMETABLE_ID, TENANT_ID)).thenReturn(Optional.of(tt));

        Term term = new Term();
        term.setAcademicYearId(ACADEMIC_YEAR_ID);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 12, 31));
        when(termRepository.findByIdAndTenantId(TERM_ID, TENANT_ID)).thenReturn(Optional.of(term));

        when(holidayCalendarRepository.findByAcademicYearIdAndTenantId(ACADEMIC_YEAR_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThat(service.getForbiddenSlots(TIMETABLE_ID)).isEmpty();
    }

    @Test
    void getForbiddenSlots_timetableNotFound_throws() {
        when(timetableRepository.findByIdAndTenantId(TIMETABLE_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForbiddenSlots(TIMETABLE_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * Covers the in-memory expansion path only: repositories are mocked, so this does not measure real DB latency.
     */
    @Test
    void getForbiddenSlots_365DistinctHolidays_mocksOnly_completesWithin100Milliseconds() {
        Timetable tt = new Timetable();
        tt.setTermId(TERM_ID);
        tt.setBellScheduleId(BELL_ID);
        when(timetableRepository.findByIdAndTenantId(TIMETABLE_ID, TENANT_ID)).thenReturn(Optional.of(tt));

        Term term = new Term();
        term.setAcademicYearId(ACADEMIC_YEAR_ID);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 12, 31));
        when(termRepository.findByIdAndTenantId(TERM_ID, TENANT_ID)).thenReturn(Optional.of(term));

        HolidayCalendar cal = new HolidayCalendar();
        setField(cal, "id", CALENDAR_ID);
        when(holidayCalendarRepository.findByAcademicYearIdAndTenantId(ACADEMIC_YEAR_ID, TENANT_ID))
                .thenReturn(Optional.of(cal));

        List<HolidayDate> rows = new ArrayList<>();
        LocalDate d0 = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 365; i++) {
            HolidayDate hd = new HolidayDate();
            hd.setDate(d0.plusDays(i));
            rows.add(hd);
        }
        when(holidayDateRepository.findByHolidayCalendarIdOrderByDateAsc(CALENDAR_ID)).thenReturn(rows);

        SchedulePeriod p1 = period(200L, 1, false);
        when(schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(BELL_ID)).thenReturn(List.of(p1));

        long t0 = System.nanoTime();
        List<PeriodSlot> slots = service.getForbiddenSlots(TIMETABLE_ID);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        assertThat(slots).hasSize(365);
        assertThat(ms).isLessThan(100L);
    }

    private static SchedulePeriod period(Long id, int ordinal, boolean isBreak) {
        SchedulePeriod p = new SchedulePeriod();
        setField(p, "id", id);
        p.setOrdinal(ordinal);
        p.setBreak(isBreak);
        return p;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
