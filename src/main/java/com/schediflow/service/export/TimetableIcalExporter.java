package com.schediflow.service.export;

import com.schediflow.dto.response.TimetableExportRow;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Renders a person's lessons as an iCalendar feed (EXPORT-03).
 *
 * <p>One VEVENT per lesson at its concrete date and period times. The story mentions a weekly
 * RRULE, but lessons in this schema are individual dated occurrences rather than weekly templates
 * — a recurrence rule on top of already-dated rows would duplicate every lesson. Emitting the
 * occurrences directly also makes "exclude holiday dates" a simple filter rather than an EXDATE
 * list.</p>
 */
public final class TimetableIcalExporter {

    private TimetableIcalExporter() {}

    public static String render(
            String calendarName, List<TimetableExportRow> rows, Set<LocalDate> holidays) {

        Calendar calendar = new Calendar();
        calendar.getProperties().add(new ProdId("-//SchediFlow//Timetable//EN"));
        calendar.getProperties().add(Version.VERSION_2_0);
        calendar.getProperties().add(CalScale.GREGORIAN);

        for (TimetableExportRow row : rows) {
            if (holidays.contains(row.scheduledDate())) {
                continue;
            }
            DateTime start = toDateTime(LocalDateTime.of(row.scheduledDate(), row.startTime()));
            DateTime end = toDateTime(LocalDateTime.of(row.scheduledDate(), row.endTime()));

            VEvent event = new VEvent(start, end, summary(row));
            event.getProperties().add(new Uid("lesson-" + row.lessonId() + "@schediflow"));
            if (row.roomName() != null && !row.roomName().isBlank()) {
                event.getProperties().add(new Location(row.roomName()));
            }
            calendar.getComponents().add(event);
        }
        return calendar.toString();
    }

    private static DateTime toDateTime(LocalDateTime value) {
        return new DateTime(Date.from(value.atZone(ZoneId.systemDefault()).toInstant()));
    }

    private static String summary(TimetableExportRow row) {
        return row.subjectName() + " — " + row.className();
    }
}
