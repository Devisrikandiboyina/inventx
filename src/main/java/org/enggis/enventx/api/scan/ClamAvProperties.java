package org.enggis.enventx.api.scan;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Scanning is on only when a host is configured; uploads are then refused if the scanner cannot be reached. */
@ConfigurationProperties(prefix = "clamav")
public record ClamAvProperties(String host, Integer port, Integer timeoutMs) {

    public ClamAvProperties {
        host = host == null || host.isBlank() ? null : host.strip();
        port = port == null ? 3310 : port;
        timeoutMs = timeoutMs == null ? 30_000 : timeoutMs;
    }

    public boolean enabled() {
        return host != null;
    }
}