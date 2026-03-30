package com.schediflow.service;

import com.schediflow.domain.HolidayCalendar;
import com.schediflow.domain.HolidayDate;
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

        ArgumentCaptor<HolidayDate> captor = ArgumentCaptor.forClass(HolidayDate.class);
        verify(holidayDateRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getHolidayCalendarId()).isEqualTo(CALENDAR_ID);
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

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static HolidayDate dateWithId(Long id, Long calendarId, LocalDate date, String name, HolidayType type) {
        HolidayDate d = new HolidayDate();
        setId(d, id);
        d.setHolidayCalendarId(calendarId);
        d.setTenantId(TENANT_ID);
        d.setDate(date);
        d.setName(name);
        d.setType(type);
        return d;
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
