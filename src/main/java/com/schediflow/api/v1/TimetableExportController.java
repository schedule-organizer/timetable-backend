package com.schediflow.api.v1;

import com.schediflow.dto.response.TimetableExportRow;
import com.schediflow.service.TimetableExportService;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.export.TimetableCsvExporter;
import com.schediflow.service.export.TimetableIcalExporter;
import com.schediflow.service.export.TimetablePdfRenderer;
import com.schediflow.service.export.TimetablePdfView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Timetable exports. ADMIN and MODERATOR only — an export is a full copy of the schedule. */
@RestController
@RequestMapping("/api/v1/timetables/{timetableId}/export")
public class TimetableExportController {

    private final TimetableExportService exportService;
    private final org.thymeleaf.TemplateEngine templateEngine;

    public TimetableExportController(
            TimetableExportService exportService, org.thymeleaf.TemplateEngine templateEngine) {
        this.exportService = exportService;
        this.templateEngine = templateEngine;
    }

    /**
     * A printable timetable grid as PDF (EXPORT-01).
     *
     * @param view CLASS, TEACHER or ROOM; defaults to CLASS
     * @return 200 with the PDF; 400 for an unknown view; 403 without ADMIN/MODERATOR;
     *         404 if the timetable is not in the tenant
     */
    @GetMapping("/pdf")
    @PreAuthorize("hasAnyRole(\'ADMIN\', \'MODERATOR\')")
    public ResponseEntity<byte[]> pdf(
            @PathVariable Long timetableId,
            @RequestParam(required = false, defaultValue = "CLASS") String view) {

        TimetablePdfView pdfView = exportService.parseView(view);
        TimetableExportService.PdfContext context = exportService.pdfContext(timetableId);

        byte[] pdf = TimetablePdfRenderer.render(
                templateEngine,
                context.schoolName(),
                context.timetableName(),
                context.termRange(),
                pdfView,
                context.rows());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"timetable-" + timetableId + "-"
                                + pdfView.name().toLowerCase() + ".pdf\"")
                .body(pdf);
    }

    /**
     * The timetable's lessons as CSV (EXPORT-02).
     *
     * <p>Rendered into memory rather than streamed. The story suggests streaming to avoid holding
     * every lesson at once, but the rows must be loaded up front anyway so that a missing timetable
     * is a clean 404 rather than a half-written 200 — so streaming would only have avoided
     * buffering the rendered text, at the cost of an async response that fights the security filter
     * chain. True end-to-end streaming needs a cursor-based read; recorded as deferred work.</p>
     *
     * @return 200 with the file; 403 without ADMIN/MODERATOR; 404 if the timetable is not in the tenant
     */
    @GetMapping("/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<byte[]> csv(@PathVariable Long timetableId) throws IOException {
        List<TimetableExportRow> rows = exportService.loadRows(timetableId);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        TimetableCsvExporter.write(buffer, rows);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"timetable-" + timetableId + ".csv\"")
                .body(buffer.toByteArray());
    }

    /**
     * One person's lessons as an .ics feed (EXPORT-03). Teachers may export their own schedule;
     * ADMIN and MODERATOR may export anyone's.
     *
     * @return 200 with the calendar; 403 if a teacher asks for someone else;
     *         404 if the timetable or user is not in the tenant
     */
    @GetMapping("/ical")
    public ResponseEntity<byte[]> ical(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long timetableId,
            @RequestParam Long userId) {

        List<TimetableExportRow> rows = exportService.loadRowsForUser(principal, timetableId, userId);
        String ics = TimetableIcalExporter.render(
                "SchediFlow", rows, exportService.holidaysCovering(rows));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"schedule-" + timetableId + "-" + userId + ".ics\"")
                .body(ics.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
