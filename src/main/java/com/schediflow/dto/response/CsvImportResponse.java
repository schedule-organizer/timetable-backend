package com.schediflow.dto.response;

import java.util.List;

/**
 * Outcome of a bulk CSV import. Rows are processed independently, so a file can be partially
 * applied: {@code imported} + {@code updated} + {@code skipped} equals {@code totalRows}.
 *
 * @param row 1-based line number in the uploaded file (the header is line 1)
 */
public record CsvImportResponse(
        String entityType,
        int totalRows,
        int imported,
        int updated,
        int skipped,
        List<CsvImportErrorResponse> errors
) {

    public record CsvImportErrorResponse(int row, String field, String error) {}
}
