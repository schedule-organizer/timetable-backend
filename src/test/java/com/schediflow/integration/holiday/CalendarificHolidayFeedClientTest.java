package com.schediflow.integration.holiday;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.exception.BadGatewayException;
import com.schediflow.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalendarificHolidayFeedClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchPublicHolidays_missingApiKey_throwsBadRequest() {
        CalendarificHolidayFeedClient client =
                new CalendarificHolidayFeedClient(mock(RestClient.class), objectMapper, "");

        assertThatThrownBy(() -> client.fetchPublicHolidays("US", 2026, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CALENDARIFIC_API_KEY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchPublicHolidays_networkError_throwsBadGateway() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        when(restClient.get().uri(any(Function.class)).retrieve().body(String.class))
                .thenThrow(new RestClientException("connection refused"));

        CalendarificHolidayFeedClient client =
                new CalendarificHolidayFeedClient(restClient, objectMapper, "test-key");

        assertThatThrownBy(() -> client.fetchPublicHolidays("US", 2026, null))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchPublicHolidays_upstreamErrorCode_throwsBadGateway() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        when(restClient.get().uri(any(Function.class)).retrieve().body(String.class))
                .thenReturn("{\"meta\":{\"code\":401},\"response\":{}}");

        CalendarificHolidayFeedClient client =
                new CalendarificHolidayFeedClient(restClient, objectMapper, "test-key");

        assertThatThrownBy(() -> client.fetchPublicHolidays("US", 2026, null))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("Unable to fetch public holidays");
    }

    @Test
    void mergeByDate_mergesSameDate() {
        List<HolidayFeedItem> merged = CalendarificHolidayFeedClient.mergeByDate(List.of(
                new HolidayFeedItem("A", LocalDate.of(2026, 1, 1)),
                new HolidayFeedItem("B", LocalDate.of(2026, 1, 1))));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(merged.get(0).name()).contains("A").contains("B");
    }

    @Test
    void mergeByDate_keepsDistinctDates() {
        List<HolidayFeedItem> merged = CalendarificHolidayFeedClient.mergeByDate(List.of(
                new HolidayFeedItem("A", LocalDate.of(2026, 1, 1)),
                new HolidayFeedItem("B", LocalDate.of(2026, 1, 2))));

        assertThat(merged).hasSize(2);
    }
}
