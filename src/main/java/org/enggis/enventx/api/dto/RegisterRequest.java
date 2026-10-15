package org.enggis.enventx.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.enggis.enventx.api.validation.AllowedEmailDomain;

import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 254) @AllowedEmailDomain String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {

    public RegisterRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
