package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {

    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
