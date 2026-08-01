package com.schediflow.service.csv;

/**
 * A single CSV row failed validation. Carries the offending column so the caller can report
 * {@code {row, field, error}} without failing the whole import.
 */
public class CsvRowException extends RuntimeException {

    private final String field;

    public CsvRowException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
