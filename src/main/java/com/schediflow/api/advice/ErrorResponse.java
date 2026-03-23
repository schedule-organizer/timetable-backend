package com.schediflow.api.advice;

import java.time.Instant;

/**
 * Standard error envelope returned by all API error responses.
 * Shape: { status, code, message, details, timestamp }
 */
public record ErrorResponse(int status, String code, String message, Object details, String timestamp) {

    public static ErrorResponse of(int status, String code, String message, Object details) {
        return new ErrorResponse(status, code, message, details, Instant.now().toString());
    }
}
