package com.schediflow.service.export;

import com.schediflow.dto.response.TimetableExportRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes a timetable's lessons as CSV (EXPORT-02).
 *
 * <p>Written straight to the response stream so a large timetable is never buffered whole, and
 * prefixed with a UTF-8 BOM because Excel otherwise reads accented names as mojibake.</p>
 */
public final class TimetableCsvExporter {

    private static final String UTF8_BOM = "﻿";

    private static final String[] HEADERS = {
        "lessonId", "subject", "teacher", "room", "class",
        "dayOfWeek", "periodName", "startTime", "endTime"
    };

    private TimetableCsvExporter() {}

    public static void write(OutputStream out, List<TimetableExportRow> rows) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(UTF8_BOM);

        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS)
                .build())) {
            for (TimetableExportRow row : rows) {
                printer.printRecord(
                        row.lessonId(),
                        row.subjectName(),
                        row.teacherName(),
                        row.roomName() == null ? "" : row.roomName(),
                        row.className(),
                        row.dayOfWeek(),
                        row.periodName(),
                        row.startTime(),
                        row.endTime());
            }
            printer.flush();
        }
    }
}
