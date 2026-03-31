package com.schediflow.service;

import com.schediflow.domain.HolidayCalendar;
import com.schediflow.domain.HolidayDate;
import com.schediflow.domain.HolidaySource;
import com.schediflow.domain.HolidayType;
import com.schediflow.dto.request.HolidayImportRequest;
import com.schediflow.dto.response.HolidayImportResponse;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.integration.holiday.HolidayFeedClient;
import com.schediflow.integration.holiday.HolidayFeedItem;
import com.schediflow.repository.HolidayCalendarRepository;
import com.schediflow.repository.HolidayDateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HolidayImportServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long CALENDAR_ID = 50L;

    @Mock HolidayFeedClient holidayFeedClient;
    @Mock HolidayCalendarRepository holidayCalendarRepository;
    @Mock HolidayDateRepository holidayDateRepository;

    @InjectMocks HolidayImportService service;

    @Test
    void importPublicHolidays_calendarMissing_throws() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID)).thenReturn(Optional.empty());

        HolidayImportRequest req = new HolidayImportRequest(CALENDAR_ID, "US", null, 2026);

        assertThatThrownBy(() -> service.importPublicHolidays(TENANT_ID, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Holiday calendar not found");

        verify(holidayFeedClient, never()).fetchPublicHolidays(any(), anyInt(), any());
    }

    @Test
    void importPublicHolidays_insertsNewRows_returnsImportedCount() {
        stubCalendarExists();
        when(holidayFeedClient.fetchPublicHolidays("US", 2026, null))
                .thenReturn(List.of(
                        new HolidayFeedItem("A", LocalDate.of(2026, 1, 1)),
                        new HolidayFeedItem("B", LocalDate.of(2026, 7, 4))));
        when(holidayDateRepository.findByHolidayCalendarIdAndTenantIdAndDate(eq(CALENDAR_ID), eq(TENANT_ID), any()))
                .thenReturn(Optional.empty());
        when(holidayDateRepository.save(any(HolidayDate.class))).thenAnswer(inv -> inv.getArgument(0));

        HolidayImportResponse r = service.importPublicHolidays(
                TENANT_ID, new HolidayImportRequest(CALENDAR_ID, "us", null, 2026));

        assertThat(r.imported()).isEqualTo(2);
        assertThat(r.updated()).isZero();
        assertThat(r.skipped()).isZero();

        ArgumentCaptor<HolidayDate> cap = ArgumentCaptor.forClass(HolidayDate.class);
        verify(holidayDateRepository, times(2)).save(cap.capture());
        assertThat(cap.getAllValues()).allMatch(d -> HolidaySource.IMPORTED.equals(d.getSource()));
    }

    @Test
    void importPublicHolidays_sameData_skips() {
        stubCalendarExists();
        HolidayDate existing = new HolidayDate();
        existing.setName("A");
        existing.setType(HolidayType.PUBLIC_HOLIDAY);
        when(holidayFeedClient.fetchPublicHolidays("US", 2026, null))
                .thenReturn(List.of(new HolidayFeedItem("A", LocalDate.of(2026, 1, 1))));
        when(holidayDateRepository.findByHolidayCalendarIdAndTenantIdAndDate(
                        CALENDAR_ID, TENANT_ID, LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.of(existing));

        HolidayImportResponse r = service.importPublicHolidays(
                TENANT_ID, new HolidayImportRequest(CALENDAR_ID, "US", null, 2026));

        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.imported()).isZero();
        assertThat(r.updated()).isZero();
        verify(holidayDateRepository, never()).save(any());
    }

    @Test
    void importPublicHolidays_nameChange_updates() {
        stubCalendarExists();
        HolidayDate existing = new HolidayDate();
        existing.setName("Old");
        existing.setType(HolidayType.PUBLIC_HOLIDAY);
        when(holidayFeedClient.fetchPublicHolidays("US", 2026, null))
                .thenReturn(List.of(new HolidayFeedItem("New", LocalDate.of(2026, 1, 1))));
        when(holidayDateRepository.findByHolidayCalendarIdAndTenantIdAndDate(
                        CALENDAR_ID, TENANT_ID, LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.of(existing));
        when(holidayDateRepository.save(any(HolidayDate.class))).thenAnswer(inv -> inv.getArgument(0));

        HolidayImportResponse r = service.importPublicHolidays(
                TENANT_ID, new HolidayImportRequest(CALENDAR_ID, "US", null, 2026));

        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.imported()).isZero();
        assertThat(r.skipped()).isZero();

        ArgumentCaptor<HolidayDate> cap = ArgumentCaptor.forClass(HolidayDate.class);
        verify(holidayDateRepository).save(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("New");
    }

    @Test
    void importPublicHolidays_schoolBreakRow_updatesToPublicHoliday() {
        stubCalendarExists();
        HolidayDate existing = new HolidayDate();
        existing.setName("Spring");
        existing.setType(HolidayType.SCHOOL_BREAK);
        when(holidayFeedClient.fetchPublicHolidays("US", 2026, null))
                .thenReturn(List.of(new HolidayFeedItem("Spring", LocalDate.of(2026, 3, 15))));
        when(holidayDateRepository.findByHolidayCalendarIdAndTenantIdAndDate(
                        CALENDAR_ID, TENANT_ID, LocalDate.of(2026, 3, 15)))
                .thenReturn(Optional.of(existing));
        when(holidayDateRepository.save(any(HolidayDate.class))).thenAnswer(inv -> inv.getArgument(0));

        HolidayImportResponse r = service.importPublicHolidays(
                TENANT_ID, new HolidayImportRequest(CALENDAR_ID, "US", null, 2026));

        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.skipped()).isZero();

        ArgumentCaptor<HolidayDate> cap = ArgumentCaptor.forClass(HolidayDate.class);
        verify(holidayDateRepository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(HolidayType.PUBLIC_HOLIDAY);
    }

    private void stubCalendarExists() {
        when(holidayCalendarRepository.findByIdAndTenantId(CALENDAR_ID, TENANT_ID))
                .thenReturn(Optional.of(new HolidayCalendar()));
    }
}
