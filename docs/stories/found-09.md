# Story FOUND-09 — Global Exception Handler
**Epic:** Epic 1 — Foundation & Infrastructure | **Points:** 2 SP | **Status:** Not Started

## Description
`GlobalExceptionHandler`: consistent JSON error envelope (`status`, `code`, `message`, `details`, `timestamp`) for all error types

## Acceptance Criteria
- [ ] `@RestControllerAdvice` `GlobalExceptionHandler` handles all uncaught exceptions
- [ ] Error response JSON shape: `{ status, code, message, details, timestamp }`
- [ ] Handles: `MethodArgumentNotValidException` (400), `AccessDeniedException` (403), `EntityNotFoundException` (404), generic `Exception` (500)
- [ ] Validation errors include field-level `details` array
- [ ] No stack traces exposed in response body
- [ ] Consistent format regardless of error type

## Technical Notes
Use `@ExceptionHandler` methods. Timestamp in ISO-8601 format.
