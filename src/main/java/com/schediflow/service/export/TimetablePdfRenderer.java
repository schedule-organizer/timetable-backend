package com.schediflow.service.export;

import com.schediflow.dto.response.TimetableExportRow;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Renders a printable timetable grid as PDF (EXPORT-01).
 *
 * <p>TD-05 resolved in favour of Flying Saucer: an HTML template through Thymeleaf, then
 * XHTML → PDF. It runs in-process with no browser to install, which is what makes the story's
 * "under 10 seconds" target comfortable; headless Chrome would only be needed for pixel-perfect
 * fidelity the ACs do not ask for.</p>
 */
public final class TimetablePdfRenderer {

    private TimetablePdfRenderer() {}

    /**
     * @param groupLabel what each section of the grid is titled by, derived from the chosen view
     */
    public record Cell(String groupLabel, String periodName, int dayOfWeek, List<String> lines) {}

    public static byte[] render(
            TemplateEngine templateEngine,
            String schoolName,
            String timetableName,
            String termRange,
            TimetablePdfView view,
            List<TimetableExportRow> rows) {

        Set<String> periodNames = rows.stream()
                .sorted(Comparator.comparing(TimetableExportRow::periodOrdinal))
                .map(TimetableExportRow::periodName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> days = rows.stream()
                .map(TimetableExportRow::dayOfWeek)
                .collect(Collectors.toCollection(TreeSet::new));

        // group → period → day → the lines printed in that cell
        Map<String, Map<String, Map<Integer, List<String>>>> grid = new LinkedHashMap<>();
        for (String group : groupsOf(view, rows)) {
            grid.put(group, new LinkedHashMap<>());
        }
        for (TimetableExportRow row : rows) {
            String group = groupOf(view, row);
            grid.computeIfAbsent(group, g -> new LinkedHashMap<>())
                    .computeIfAbsent(row.periodName(), p -> new LinkedHashMap<>())
                    .computeIfAbsent(row.dayOfWeek(), d -> new ArrayList<>())
                    .add(cellText(view, row));
        }

        Context context = new Context();
        context.setVariable("schoolName", schoolName);
        context.setVariable("timetableName", timetableName);
        context.setVariable("termRange", termRange);
        context.setVariable("view", view.name());
        context.setVariable("periodNames", periodNames);
        context.setVariable("days", days);
        context.setVariable("grid", grid);

        String html = templateEngine.process("export/timetable-pdf", context);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html);
        renderer.layout();
        renderer.createPDF(out);
        return out.toByteArray();
    }

    private static Set<String> groupsOf(TimetablePdfView view, List<TimetableExportRow> rows) {
        return rows.stream()
                .map(row -> groupOf(view, row))
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String groupOf(TimetablePdfView view, TimetableExportRow row) {
        return switch (view) {
            case CLASS -> row.className();
            case TEACHER -> row.teacherName();
            // A lesson may have no room; group those together rather than dropping them.
            case ROOM -> row.roomName() == null ? "(no room)" : row.roomName();
        };
    }

    /** Each cell shows what the chosen view does not already state in its heading. */
    private static String cellText(TimetablePdfView view, TimetableExportRow row) {
        return switch (view) {
            case CLASS -> row.subjectName() + " · " + row.teacherName()
                    + (row.roomName() == null ? "" : " · " + row.roomName());
            case TEACHER -> row.subjectName() + " · " + row.className()
                    + (row.roomName() == null ? "" : " · " + row.roomName());
            case ROOM -> row.subjectName() + " · " + row.className() + " · " + row.teacherName();
        };
    }
}
