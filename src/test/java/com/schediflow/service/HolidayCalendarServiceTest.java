package com.schediflow.service;

import com.schediflow.domain.AcademicYear;
import com.schediflow.domain.HolidayCalendar;
import com.schediflow.dto.request.HolidayCalendarRequest;
import com.schediflow.dto.response.HolidayCalendarResponse;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.AcademicYearRepository;
import com.schediflow.repository.HolidayCalendarRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HolidayCalendarServiceTest {

    @Mock HolidayCalendarRepository holidayCalendarRepository;
    @Mock AcademicYearRepository academicYearRepository;

    HolidayCalendarService service;

    private static final Long TENANT_ID = 1L;
    private static final Long YEAR_ID = 10L;
    private static final Long CALENDAR_ID = 100L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new HolidayCalendarService(holidayCalendarRepository, academicYearRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Happy path ──────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savesAndReturnsResponse() {
        when(academicYearRepository.findByIdAndTenantId(YEAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new AcademicYear()));
        when(holidayCalendarRepository.existsByAcademicYearIdAndTenantId(YEAR_ID, TENANT_ID))
                .thenReturn(false);
        when(holidayCalendarRepository.save(any(HolidayCalendar.class)))
                .thenAnswer(inv -> {
                    HolidayCalendar cal = inv.getArgument(0);
                    setId(cal, CALENDAR_ID);
                    return cal;
                });

        HolidayCalendarRequest req = new HolidayCalendarRequest(YEAR_ID, "Summer Break", "US", "CA");
        HolidayCalendarResponse resp = service.create(TENANT_ID, req);

        assertThat(resp.id()).isEqualTo(CALENDAR_ID);
        assertThat(resp.academicYearId()).isEqualTo(YEAR_ID);
        assertThat(resp.name()).isEqualTo("Summer Break");
        assertThat(resp.country()).isEqualTo("US");
        assertThat(resp.region()).isEqualTo("CA");

        ArgumentCaptor<HolidayCalendar> captor = ArgumentCaptor.forClass(HolidayCalendar.class);
        verify(holidayCalendarRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void create_withNullCountryRegion_saves() {
        when(academicYearRepository.findByIdAndTenantId(YEAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new AcademicYear()));
        when(holidayCalendarRepository.existsByAcademicYearIdAndTenantId(YEAR_ID, TENANT_ID))
                .thenReturn(false);
        when(holidayCalendarRepository.save(any())).thenAnswer(inv -> {
            HolidayCalendar cal = inv.getArgument(0);
            setId(cal, CALENDAR_ID);
            return cal;
        });

        HolidayCalendarRequest req = new HolidayCalendarRequest(YEAR_ID, "Calendar", null, null);
        HolidayCalendarResponse resp = service.create(TENANT_ID, req);

        assertThat(resp.country()).isNull();
        assertThat(resp.region()).isNull();
    }

    @Test
    void update_changesFields() {
        HolidayCalendar existing = calendarWithId(CALENDAR_ID, YEAR_ID);
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(academicYearRepository.findByIdAndTenantId(YEAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new AcademicYear()));
        when(holidayCalendarRepository.existsByAcademicYearIdAndTenantIdAndIdNot(YEAR_ID, TENANT_ID, CALENDAR_ID))
                .thenReturn(false);
        when(holidayCalendarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HolidayCalendarRequest req = new HolidayCalendarRequest(YEAR_ID, "Updated Name", "GB", null);
        HolidayCalendarResponse resp = service.update(TENANT_ID, CALENDAR_ID, req);

        assertThat(resp.name()).isEqualTo("Updated Name");
        assertThat(resp.country()).isEqualTo("GB");
    }

    @Test
    void delete_callsRepository() {
        HolidayCalendar existing = calendarWithId(CALENDAR_ID, YEAR_ID);
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        service.delete(CALENDAR_ID);

        verify(holidayCalendarRepository).delete(existing);
    }

    @Test
    void list_returnsMappedResponses() {
        HolidayCalendar cal1 = calendarWithId(1L, YEAR_ID);
        HolidayCalendar cal2 = calendarWithId(2L, YEAR_ID);
        when(holidayCalendarRepository.findAll()).thenReturn(List.of(cal1, cal2));

        List<HolidayCalendarResponse> result = service.list();

        assertThat(result).hasSize(2);
    }

    @Test
    void getById_returnsResponse() {
        HolidayCalendar cal = calendarWithId(CALENDAR_ID, YEAR_ID);
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(cal));

        HolidayCalendarResponse resp = service.getById(CALENDAR_ID);

        assertThat(resp.id()).isEqualTo(CALENDAR_ID);
    }

    // ── Error paths ─────────────────────────────────────────────────────────────

    @Test
    void create_academicYearNotFound_throwsResourceNotFoundException() {
        when(academicYearRepository.findByIdAndTenantId(YEAR_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TENANT_ID, new HolidayCalendarRequest(YEAR_ID, "X", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(holidayCalendarRepository, never()).save(any());
    }

    @Test
    void create_duplicateCalendar_throwsConflictException() {
        when(academicYearRepository.findByIdAndTenantId(YEAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new AcademicYear()));
        when(holidayCalendarRepository.existsByAcademicYearIdAndTenantId(YEAR_ID, TENANT_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(TENANT_ID, new HolidayCalendarRequest(YEAR_ID, "Dup", null, null)))
                .isInstanceOf(ConflictException.class);

        verify(holidayCalendarRepository, never()).save(any());
    }

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        when(holidayCalendarRepository.findByIdAndTenantId(99L, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_calendarNotFound_throwsResourceNotFoundException() {
        when(holidayCalendarRepository.findByIdAndTenantId(99L, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(TENANT_ID, 99L, new HolidayCalendarRequest(YEAR_ID, "X", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_changeToAlreadyUsedYear_throwsConflictException() {
        Long otherYearId = 20L;
        HolidayCalendar existing = calendarWithId(CALENDAR_ID, YEAR_ID);
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(academicYearRepository.findByIdAndTenantId(otherYearId, TENANT_ID))
                .thenReturn(Optional.of(new AcademicYear()));
        when(holidayCalendarRepository.existsByAcademicYearIdAndTenantIdAndIdNot(otherYearId, TENANT_ID, CALENDAR_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(TENANT_ID, CALENDAR_ID, new HolidayCalendarRequest(otherYearId, "X", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        when(holidayCalendarRepository.findByIdAndTenantId(99L, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static HolidayCalendar calendarWithId(Long id, Long yearId) {
        HolidayCalendar cal = new HolidayCalendar();
        setId(cal, id);
        cal.setTenantId(TENANT_ID);
        cal.setAcademicYearId(yearId);
        cal.setName("Calendar " + id);
        return cal;
    }

    private static void setId(HolidayCalendar cal, Long id) {
        try {
            Field field = HolidayCalendar.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(cal, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
