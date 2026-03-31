package com.schediflow.integration.holiday;

import java.util.List;

/**
 * Fetches public holidays for a country (and optional region) for a given year.
 * Implemented by {@link CalendarificHolidayFeedClient} for production.
 */
public interface HolidayFeedClient {

    /**
     * @param country ISO-3166 alpha-2 country code (e.g. {@code US})
     * @param year calendar year
     * @param region optional Calendarific {@code location} parameter (e.g. {@code us-ny}); may be null
     */
    List<HolidayFeedItem> fetchPublicHolidays(String country, int year, String region);
}
