package org.enggis.enventx.api.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(int status, String code, String message, List<FieldError> errors, Instant timestamp)
        implements ApiResponse<Void> {

    public ErrorResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    @Override
    public boolean success() {
        return false;
    }
}
