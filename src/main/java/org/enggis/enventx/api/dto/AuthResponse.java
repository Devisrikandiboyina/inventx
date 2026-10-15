package org.enggis.enventx.api.dto;

public record AuthResponse(UserDto user, String accessToken) {
}
