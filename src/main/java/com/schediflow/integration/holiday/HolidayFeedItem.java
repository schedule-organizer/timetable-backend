package com.schediflow.integration.holiday;

import java.time.LocalDate;

/**
 * Normalized row from a public holiday feed (e.g. Calendarific).
 */
public record HolidayFeedItem(String name, LocalDate date) {
}
