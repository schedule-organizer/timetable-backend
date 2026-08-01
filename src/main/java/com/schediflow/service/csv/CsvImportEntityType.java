package com.schediflow.service.csv;

import java.util.List;

/**
 * Entity kinds accepted by {@code POST /api/v1/import/{entityType}}, with the CSV columns each one
 * requires. Header matching is case-insensitive.
 */
public enum CsvImportEntityType {
    ROOMS("rooms", List.of("name", "type")),
    CLASSES("classes", List.of("name")),
    TEACHERS("teachers", List.of("email", "displayName"));

    private final String pathValue;
    private final List<String> requiredHeaders;

    CsvImportEntityType(String pathValue, List<String> requiredHeaders) {
        this.pathValue = pathValue;
        this.requiredHeaders = requiredHeaders;
    }

    public String pathValue() {
        return pathValue;
    }

    public List<String> requiredHeaders() {
        return requiredHeaders;
    }
}
