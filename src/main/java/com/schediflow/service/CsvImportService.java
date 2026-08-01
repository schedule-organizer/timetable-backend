package com.schediflow.service;

import com.schediflow.dto.response.CsvImportResponse;
import com.schediflow.dto.response.CsvImportResponse.CsvImportErrorResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.security.TenantContext;
import com.schediflow.service.csv.CsvImportEntityType;
import com.schediflow.service.csv.CsvRow;
import com.schediflow.service.csv.CsvRowException;
import com.schediflow.service.csv.CsvRowWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bulk CSV import for rooms, classes and teachers (RES-11).
 *
 * <p>Rows are independent: each valid row is upserted in its own transaction and each invalid row
 * is reported as {@code {row, field, error}} without stopping the import. File-level problems
 * (unknown entity type, oversized file, too many rows, unreadable CSV, missing required column)
 * reject the whole upload with a 400.</p>
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final int MAX_ROWS = 1000;

    private final CsvRowWriter rowWriter;

    public CsvImportService(CsvRowWriter rowWriter) {
        this.rowWriter = rowWriter;
    }

    public CsvImportResponse importCsv(String entityTypeRaw, MultipartFile file) {
        Long tenantId = TenantContext.getTenantId();
        CsvImportEntityType entityType = parseEntityType(entityTypeRaw);
        assertUploadable(file);

        List<CsvRow> rows = parse(file, entityType);

        int imported = 0;
        int updated = 0;
        List<CsvImportErrorResponse> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int lineNumber = i + 2; // header occupies line 1
            try {
                CsvRowWriter.Outcome outcome = switch (entityType) {
                    case ROOMS -> rowWriter.upsertRoom(tenantId, rows.get(i));
                    case CLASSES -> rowWriter.upsertClass(tenantId, rows.get(i));
                    case TEACHERS -> rowWriter.upsertTeacher(tenantId, rows.get(i));
                };
                if (outcome == CsvRowWriter.Outcome.IMPORTED) {
                    imported++;
                } else {
                    updated++;
                }
            } catch (CsvRowException e) {
                errors.add(new CsvImportErrorResponse(lineNumber, e.getField(), e.getMessage()));
            } catch (RuntimeException e) {
                log.warn("CSV import row {} failed for entityType={}", lineNumber, entityType.pathValue(), e);
                errors.add(new CsvImportErrorResponse(lineNumber, null, "Row could not be saved"));
            }
        }

        return new CsvImportResponse(
                entityType.pathValue(), rows.size(), imported, updated, errors.size(), errors);
    }

    private static CsvImportEntityType parseEntityType(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(CsvImportEntityType.values())
                .filter(t -> t.pathValue().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Unsupported import entity type: " + raw + ". Must be one of: "
                                + Arrays.stream(CsvImportEntityType.values())
                                        .map(CsvImportEntityType::pathValue)
                                        .collect(Collectors.joining(", "))));
    }

    private static void assertUploadable(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("CSV file exceeds the 5MB limit");
        }
    }

    private List<CsvRow> parse(MultipartFile file, CsvImportEntityType entityType) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                // A repeated column would otherwise silently shadow the earlier one.
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                CSVParser parser = CSVParser.parse(reader, format)) {

            Map<String, Integer> headerMap = lowerCasedHeaders(parser.getHeaderMap());
            for (String required : entityType.requiredHeaders()) {
                if (!headerMap.containsKey(required.toLowerCase(Locale.ROOT))) {
                    throw new BadRequestException("CSV is missing the required column: " + required);
                }
            }

            List<CsvRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                CsvRow row = toRow(record, headerMap);
                if (row.isBlank()) {
                    continue;
                }
                rows.add(row);
                if (rows.size() > MAX_ROWS) {
                    throw new BadRequestException("CSV exceeds the maximum of " + MAX_ROWS + " rows");
                }
            }
            return rows;
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException("CSV could not be parsed: " + e.getMessage());
        }
    }

    private static Map<String, Integer> lowerCasedHeaders(Map<String, Integer> headerMap) {
        Map<String, Integer> normalized = new HashMap<>();
        headerMap.forEach((header, index) -> {
            if (header != null && !header.isBlank()) {
                normalized.put(header.trim().toLowerCase(Locale.ROOT), index);
            }
        });
        return normalized;
    }

    private static CsvRow toRow(CSVRecord record, Map<String, Integer> headerMap) {
        Map<String, String> values = new HashMap<>();
        headerMap.forEach((header, index) -> {
            String value = index < record.size() ? record.get(index) : null;
            values.put(header, value);
        });
        return new CsvRow(values);
    }
}
