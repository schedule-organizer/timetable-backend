package com.schediflow.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Converts List<String> to a pipe-delimited TEXT column and back.
 * Pipe (|) is used instead of comma so that tag values containing commas are stored correctly.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final String DELIMITER = "|";
    private static final String SPLIT_PATTERN = Pattern.quote(DELIMITER);

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return String.join(DELIMITER, attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(dbData.split(SPLIT_PATTERN, -1))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
