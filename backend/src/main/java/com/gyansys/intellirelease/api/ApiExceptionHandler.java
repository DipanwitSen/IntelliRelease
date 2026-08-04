package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.application.ApprovalRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Structured JSON for every error path.
 *
 * <p>{@link ApprovalRequiredException} is the important one: it maps to
 * <strong>409 APPROVAL_REQUIRED</strong>, which is the demonstrable proof that
 * the approval gate lives in the backend. A UI that hides the button is a
 * convention; a backend that refuses the call is a control.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Machine-readable error body. {@code code} is stable; {@code message} is for humans. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(
            String code,
            String message,
            List<String> details,
            String correlationId,
            OffsetDateTime timestamp
    ) {
        static ApiError of(String code, String message, List<String> details) {
            return new ApiError(code, message, details, MDC.get("correlationId"), OffsetDateTime.now());
        }
    }

    @ExceptionHandler(ApprovalRequiredException.class)
    public ResponseEntity<ApiError> handleApprovalRequired(ApprovalRequiredException exception) {
        log.info("Blocked unapproved dispatch: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("APPROVAL_REQUIRED", exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("INVALID_STATE", exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("BAD_REQUEST", exception.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDATION_FAILED", "Request validation failed", details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        // Log the detail, return a correlation ID. Stack traces are for the log,
        // not for the caller.
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR",
                        "An unexpected error occurred. Quote the correlation ID when reporting it.",
                        null));
    }
}
