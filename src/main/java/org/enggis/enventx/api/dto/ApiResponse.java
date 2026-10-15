package org.enggis.enventx.api.dto;

import java.time.Instant;
import java.util.List;

public sealed interface ApiResponse<T> permits SuccessResponse, ErrorResponse {

    boolean success();

    Instant timestamp();

    static <T> SuccessResponse<T> ok(T data) {
        return new SuccessResponse<>(data, null, Instant.now());
    }

    static <T> SuccessResponse<T> ok(T data, String message) {
        return new SuccessResponse<>(data, message, Instant.now());
    }

    static ErrorResponse error(int status, String code, String message) {
        return new ErrorResponse(status, code, message, List.of(), Instant.now());
    }

    static ErrorResponse error(int status, String code, String message, List<FieldError> errors) {
        return new ErrorResponse(status, code, message, errors, Instant.now());
    }
}
