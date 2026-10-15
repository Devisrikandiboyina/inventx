package org.enggis.enventx.api.dto;

import java.time.Instant;

public record SuccessResponse<T>(T data, String message, Instant timestamp) implements ApiResponse<T> {

    @Override
    public boolean success() {
        return true;
    }
}
