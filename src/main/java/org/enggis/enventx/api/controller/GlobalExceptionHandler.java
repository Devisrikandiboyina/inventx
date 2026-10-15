package org.enggis.enventx.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.enggis.enventx.api.dto.ErrorResponse;
import org.enggis.enventx.api.dto.FieldError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Maps every failure to the {@link ErrorResponse} envelope; {@code code} is the machine-readable reason. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException e) {
        var status = e.getStatusCode();
        var reason = e.getReason();
        var code = reason == null || reason.isBlank()
                ? defaultCode(status)
                : reason.contains(":") ? reason.substring(0, reason.indexOf(':')).strip() : reason;
        return body(status, code, reason == null ? code : reason, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        var errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> invalidParameter(HandlerMethodValidationException e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", List.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> malformed(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request could not be parsed", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> denied(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> unauthenticated(AuthenticationException e) {
        return body(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required", List.of());
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflict(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse framework) {
            // 404 / 405 / 415 and similar raised by Spring MVC itself keep their own status
            var status = framework.getStatusCode();
            return body(status, defaultCode(status), framework.getBody().getTitle() == null
                    ? defaultCode(status) : framework.getBody().getTitle(), List.of());
        }
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", List.of());
    }

    private static String defaultCode(HttpStatusCode status) {
        return status instanceof HttpStatus known ? known.name() : "HTTP_" + status.value();
    }

    private static ResponseEntity<ErrorResponse> body(
            HttpStatusCode status, String code, String message, List<FieldError> errors) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), code, message, errors, Instant.now()));
    }
}
