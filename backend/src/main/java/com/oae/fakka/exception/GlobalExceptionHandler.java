package com.oae.fakka.exception;

import com.oae.fakka.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Translates every exception escaping a controller into {@link ErrorResponse}.
 * <p>
 * Two rules hold throughout: a caller never sees an internal detail (class names,
 * SQL, stack traces), and anything unrecognised is logged at ERROR with its cause
 * rather than swallowed. Handlers are ordered most specific first.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String UNEXPECTED_ERROR_MESSAGE =
            "An unexpected error occurred. Please try again later.";

    /** Expected, deliberate failures thrown by our own services. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException exception, HttpServletRequest request) {
        return build(exception.getStatus(), exception.getMessage(), request);
    }

    /** Bean-validation failure on an {@code @Valid} request body or model attribute. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describeFieldError)
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message, request);
    }

    /** Bean-validation failure on a path variable, request param, or validated service call. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {

        String message = exception.getConstraintViolations().stream()
                .map(violation -> "%s: %s".formatted(violation.getPropertyPath(), violation.getMessage()))
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message, request);
    }

    /** Body absent, malformed JSON, or a value Jackson could not bind. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        // The raw message quotes the offending payload and Jackson internals; neither is safe to echo.
        log.debug("Unreadable request body on {}", request.getRequestURI(), exception);
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Required parameter '%s' is missing".formatted(exception.getParameterName()), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Parameter '%s' has an invalid value".formatted(exception.getName()), request);
    }

    /** No handler matched the URL — keeps 404s in the same shape as everything else. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint matched this request", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "Method %s is not supported for this endpoint".formatted(exception.getMethod()), request);
    }

    /** A unique/foreign-key constraint rejected the write. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        // Constraint names and SQL live in the cause, so log it and return a generic conflict.
        log.warn("Data integrity violation on {}", request.getRequestURI(), exception);
        return build(HttpStatus.CONFLICT, "Request conflicts with existing data", request);
    }

    /** Last resort: an unhandled exception is a bug, so log it in full and reveal nothing. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE, request);
    }

    private static String describeFieldError(FieldError fieldError) {
        return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());

        return ResponseEntity.status(status).body(body);
    }
}
