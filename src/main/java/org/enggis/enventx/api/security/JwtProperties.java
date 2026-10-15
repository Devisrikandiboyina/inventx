package org.enggis.enventx.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration, String issuer) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured (Base64, at least 256 bits)");
        }
        expiration = expiration == null ? Duration.ofHours(1) : expiration;
        issuer = issuer == null || issuer.isBlank() ? "inventx" : issuer;
    }
}
