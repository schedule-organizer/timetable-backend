package com.schediflow.service.csv;

import java.util.Locale;
import java.util.Map;

/**
 * One parsed CSV data row, keyed by lower-cased header name so column headers can be written in any
 * case ({@code displayName}, {@code DisplayName}, {@code DISPLAYNAME} are all accepted).
 */
public final class CsvRow {

    private final Map<String, String> values;

    public CsvRow(Map<String, String> values) {
        this.values = values;
    }

    public String get(String header) {
        return values.get(header.toLowerCase(Locale.ROOT));
    }

    /** True when every cell is blank — a trailing empty line rather than real data. */
    public boolean isBlank() {
        return values.values().stream().allMatch(v -> v == null || v.isBlank());
    }
}
