package com.schediflow.api.v1;

import com.schediflow.dto.response.CsvImportResponse;
import com.schediflow.service.CsvImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk CSV import for rooms, classes and teachers. ADMIN and MODERATOR only.
 *
 * <p>Expected columns (case-insensitive; only the first ones are required):</p>
 * <ul>
 *   <li><b>rooms</b> — name, type, capacity, building, floor, equipmentTags (pipe-separated)</li>
 *   <li><b>classes</b> — name, yearLevel, capacity, homeroom (an existing room name)</li>
 *   <li><b>teachers</b> — email (an existing user in the institution), displayName,
 *       maxPeriodsPerDay, maxConsecutivePeriods, workloadCap</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/import")
public class CsvImportController {

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    /**
     * Uploads a CSV file and upserts its rows. Valid rows are applied even when others fail.
     *
     * @return 200 with a summary and per-row errors; 400 for an unknown entityType, a file over 5MB,
     *         more than 1000 rows, unparseable CSV, or a missing required column; 403 without ADMIN/MODERATOR
     */
    @PostMapping(path = "/{entityType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<CsvImportResponse> importCsv(
            @PathVariable String entityType, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importCsv(entityType, file));
    }
}
