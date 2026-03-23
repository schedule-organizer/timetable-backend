package com.schediflow.exception;

/**
 * Base exception for all application-level errors.
 * Carries an error code and HTTP status so the GlobalExceptionHandler
 * can produce a consistent response without a separate handler per subclass.
 */
public class SchediFlowException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public SchediFlowException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
