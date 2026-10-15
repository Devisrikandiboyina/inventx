package org.enggis.enventx.api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credentials come only from the environment; nothing secret is stored in the repository. */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String url,
        String accessKey,
        String secretKey,
        String bucket,
        String sourceFolder,
        String region
) {

    public MinioProperties {
        sourceFolder = normalizeFolder(sourceFolder);
        region = region == null || region.isBlank() ? "us-east-1" : region;
    }

    private static String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "";
        }
        var trimmed = folder.strip().replaceAll("^/+", "");
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }
}
