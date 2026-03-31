package com.schediflow.service;

import com.schediflow.domain.HolidayCalendar;
import com.schediflow.domain.HolidayDate;
import com.schediflow.domain.HolidaySource;
import com.schediflow.domain.HolidayType;
import com.schediflow.dto.request.HolidayDateRequest;
import com.schediflow.dto.request.HolidayDateUpdateRequest;
import com.schediflow.dto.response.HolidayDateResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.HolidayCalendarRepository;
import com.schediflow.repository.HolidayDateRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HolidayDateServiceTest {

    @Mock HolidayDateRepository holidayDateRepository;
    @Mock HolidayCalendarRepository holidayCalendarRepository;

    HolidayDateService service;

    private static final Long TENANT_ID    = 1L;
    private static final Long CALENDAR_ID  = 10L;
    private static final Long DATE_ID      = 100L;
    private static final LocalDate DATE    = LocalDate.of(2026, 1, 1);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new HolidayDateService(holidayDateRepository, holidayCalendarRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── addDate — happy path ────────────────────────────────────────────────────

    @Test
    void addDate_validRequest_savesAndReturnsResponse() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new HolidayCalendar()));
        when(holidayDateRepository.existsByHolidayCalendarIdAndTenantIdAndDate(CALENDAR_ID, TENANT_ID, DATE))
                .thenReturn(false);
        when(holidayDateRepository.save(any(HolidayDate.class))).thenAnswer(inv -> {
            HolidayDate d = inv.getArgument(0);
            setId(d, DATE_ID);
            return d;
        });

        HolidayDateRequest req = new HolidayDateRequest(DATE, "New Year", HolidayType.PUBLIC_HOLIDAY);
        HolidayDateResponse resp = service.addDate(CALENDAR_ID, req);

        assertThat(resp.id()).isEqualTo(DATE_ID);
        assertThat(resp.calendarId()).isEqualTo(CALENDAR_ID);
        assertThat(resp.date()).isEqualTo(DATE);
        assertThat(resp.name()).isEqualTo("New Year");
        assertThat(resp.type()).isEqualTo(HolidayType.PUBLIC_HOLIDAY);
        assertThat(resp.source()).isEqualTo(HolidaySource.MANUAL);

        ArgumentCaptor<HolidayDate> captor = ArgumentCaptor.forClass(HolidayDate.class);
        verify(holidayDateRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getHolidayCalendarId()).isEqualTo(CALENDAR_ID);
        assertThat(captor.getValue().getSource()).isEqualTo(HolidaySource.MANUAL);
    }

    // ── addDate — error paths ───────────────────────────────────────────────────

    @Test
    void addDate_calendarNotFound_throwsResourceNotFoundException() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.addDate(CALENDAR_ID, new HolidayDateRequest(DATE, "X", HolidayType.PUBLIC_HOLIDAY)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(holidayDateRepository, never()).save(any());
    }

    @Test
    void addDate_duplicateDate_throwsBadRequestException() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new HolidayCalendar()));
        when(holidayDateRepository.existsByHolidayCalendarIdAndTenantIdAndDate(CALENDAR_ID, TENANT_ID, DATE))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.addDate(CALENDAR_ID, new HolidayDateRequest(DATE, "Dup", HolidayType.PUBLIC_HOLIDAY)))
                .isInstanceOf(BadRequestException.class);

        verify(holidayDateRepository, never()).save(any());
    }

    @Test
    void addDate_uniqueConstraintOnSave_throwsBadRequestException() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new HolidayCalendar()));
        when(holidayDateRepository.existsByHolidayCalendarIdAndTenantIdAndDate(CALENDAR_ID, TENANT_ID, DATE))
                .thenReturn(false);
        SQLException sqlEx = new SQLException("duplicate key", "23505");
        when(holidayDateRepository.save(any(HolidayDate.class)))
                .thenThrow(new DataIntegrityViolationException("constraint failed", sqlEx));

        assertThatThrownBy(() ->
                service.addDate(CALENDAR_ID, new HolidayDateRequest(DATE, "Race", HolidayType.PUBLIC_HOLIDAY)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    // ── updateDate — happy path ─────────────────────────────────────────────────

    @Test
    void updateDate_validRequest_updatesNameAndType() {
        HolidayDate existing = dateWithId(DATE_ID, CALENDAR_ID, DATE, "Old Name", HolidayType.PUBLIC_HOLIDAY);
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new HolidayCalendar()));
        when(holidayDateRepository.findByIdAndHolidayCalendarIdAndTenantId(DATE_ID, CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(holidayDateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HolidayDateUpdateRequest req = new HolidayDateUpdateRequest("New Name", HolidayType.SCHOOL_BREAK);
        HolidayDateResponse resp = service.updateDate(CALENDAR_ID, DATE_ID, req);

        assertThat(resp.name()).isEqualTo("New Name");
        assertThat(resp.type()).isEqualTo(HolidayType.SCHOOL_BREAK);
        assertThat(resp.date()).isEqualTo(DATE);
    }

    // ── updateDate — error paths ────────────────────────────────────────────────

    @Test
    void updateDate_calendarNotFound_throwsResourceNotFoundException() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.updateDate(CALENDAR_ID, DATE_ID, new HolidayDateUpdateRequest("X", HolidayType.PUBLIC_HOLIDAY)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(holidayDateRepository, never()).save(any());
    }

    @Test
    void updateDate_dateNotFound_throwsResourceNotFoundException() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new HolidayCalendar()));
        when(holidayDateRepository.findByIdAndHolidayCalendarIdAndTenantId(DATE_ID, CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.updateDate(CALENDAR_ID, DATE_ID, new HolidayDateUpdateRequest("X", HolidayType.PUBLIC_HOLIDAY)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(holidayDateRepository, never()).save(any());
    }

    // ── deleteDate — happy path ─────────────────────────────────────────────────

    @Test
    void deleteDate_callsRepository() {
        HolidayDate existing = dateWithId(DATE_ID, CALENDAR_ID, DATE, "Holiday", HolidayType.PUBLIC_HOLIDAY);
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new HolidayCalendar()));
        when(holidayDateRepository.findByIdAndHolidayCalendarIdAndTenantId(DATE_ID, CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        service.deleteDate(CALENDAR_ID, DATE_ID);

        verify(holidayDateRepository).delete(existing);
    }

    // ── deleteDate — error paths ────────────────────────────────────────────────

    @Test
    void deleteDate_calendarNotFound_throwsResourceNotFoundException() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDate(CALENDAR_ID, DATE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(holidayDateRepository, never()).delete(any());
    }

    @Test
    void deleteDate_dateNotFound_throwsResourceNotFoundException() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new HolidayCalendar()));
        when(holidayDateRepository.findByIdAndHolidayCalendarIdAndTenantId(DATE_ID, CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDate(CALENDAR_ID, DATE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(holidayDateRepository, never()).delete(any());
    }

    // ── listByAcademicYear — happy path ────────────────────────────────────────

    @Test
    void listByAcademicYear_validYear_returnsDatesInAscOrder() {
        Long academicYearId = 20L;
        HolidayCalendar cal = calendarWithId(CALENDAR_ID, academicYearId);
        HolidayDate d1 = dateWithId(1L, CALENDAR_ID, LocalDate.of(2026, 1, 1), "New Year", HolidayType.PUBLIC_HOLIDAY);
        HolidayDate d2 = dateWithId(2L, CALENDAR_ID, LocalDate.of(2026, 3, 15), "Spring Break", HolidayType.SCHOOL_BREAK);

        when(holidayCalendarRepository.findByAcademicYearIdAndTenantId(academicYearId, TENANT_ID))
                .thenReturn(Optional.of(cal));
        when(holidayDateRepository.findByHolidayCalendarIdOrderByDateAsc(CALENDAR_ID))
                .thenReturn(List.of(d1, d2));

        var result = service.listByAcademicYear(TENANT_ID, academicYearId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.get(0).source()).isEqualTo(HolidaySource.MANUAL);
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    // ── listByAcademicYear — error paths ───────────────────────────────────────

    @Test
    void listByAcademicYear_unknownAcademicYear_throwsResourceNotFoundException() {
        Long academicYearId = 999L;
        when(holidayCalendarRepository.findByAcademicYearIdAndTenantId(academicYearId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByAcademicYear(TENANT_ID, academicYearId))
                .isInstanceOf(com.schediflow.exception.ResourceNotFoundException.class);

        verify(holidayDateRepository, never()).findByHolidayCalendarIdOrderByDateAsc(any());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static HolidayDate dateWithId(Long id, Long calendarId, LocalDate date, String name, HolidayType type) {
        HolidayDate d = new HolidayDate();
        setId(d, id);
        d.setHolidayCalendarId(calendarId);
        d.setTenantId(TENANT_ID);
        d.setDate(date);
        d.setName(name);
        d.setType(type);
        d.setSource(HolidaySource.MANUAL);
        return d;
    }

    private static HolidayCalendar calendarWithId(Long id, Long academicYearId) {
        HolidayCalendar c = new HolidayCalendar();
        try {
            Field field = HolidayCalendar.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        c.setAcademicYearId(academicYearId);
        return c;
    }

    private static void setId(HolidayDate d, Long id) {
        try {
            Field field = HolidayDate.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(d, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
