package com.schediflow.api.v1;

import com.schediflow.dto.response.TimetableExportRow;
import com.schediflow.service.TimetableExportService;
import com.schediflow.service.export.TimetableCsvExporter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Timetable exports. ADMIN and MOD only — an export is a full copy of the schedule. */
@RestController
@RequestMapping("/api/v1/timetables/{timetableId}/export")
public class TimetableExportController {

    private final TimetableExportService exportService;

    public TimetableExportController(TimetableExportService exportService) {
        this.exportService = exportService;
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
     * @return 200 with the file; 403 without ADMIN/MOD; 404 if the timetable is not in the tenant
     */
    @GetMapping("/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
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
}
