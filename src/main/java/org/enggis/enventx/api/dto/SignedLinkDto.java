package org.enggis.enventx.api.dto;

import java.time.Instant;

public record SignedLinkDto(String url, Instant expiresAt) {
}
