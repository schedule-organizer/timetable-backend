package com.schediflow.api.advice;

import com.schediflow.exception.SchediFlowException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.Map;

/**
 * Translates all uncaught exceptions into the standard error envelope.
 * No stack traces are included in responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Missing required query or path parameters. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "BAD_REQUEST", ex.getMessage(), null));
    }

    /** Multipart upload larger than the configured limit (bulk CSV import). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "BAD_REQUEST", "Uploaded file exceeds the maximum allowed size", null));
    }

    /** Multipart request missing its file part, or otherwise malformed. */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> handleMultipart(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "BAD_REQUEST", "Malformed multipart request: " + ex.getMessage(), null));
    }

    /** Bean Validation failures — includes field-level details. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_ERROR", "Validation failed", fieldErrors));
    }

    /** All application-defined exceptions — status and code come from the exception itself. */
    @ExceptionHandler(SchediFlowException.class)
    public ResponseEntity<ErrorResponse> handleSchediFlow(SchediFlowException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ErrorResponse.of(ex.getHttpStatus(), ex.getCode(), ex.getMessage(), null));
    }

    /** JPA EntityManager.getReference() not found. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "NOT_FOUND", ex.getMessage(), null));
    }

    /**
     * Two concurrent edits to the same row. V027 added {@code @Version} to Lesson for SCHED-08's
     * drag-and-drop; without this the loser of the race got an opaque 500 instead of a retryable
     * conflict.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "CONFLICT",
                        "This record was changed by someone else; reload and try again", null));
    }

    /** Spring Security method-level access denied. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "FORBIDDEN", "Access denied", null));
    }

    /** Catch-all — logs the full exception but returns a safe generic message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(500, "INTERNAL_ERROR", "An unexpected error occurred", null));
    }

    private Map<String, String> toFieldError(FieldError fe) {
        return Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"
        );
    }
}
