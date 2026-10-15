package org.enggis.enventx.api.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

@Slf4j
public class MinioFileStorage implements FileStorage {

    private final MinioClient client;
    private final MinioProperties properties;

    public MinioFileStorage(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void put(String key, InputStream data, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectName(key))
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("Storage upload failed for key {}", key, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE");
        }
    }

    @Override
    public String presignedGetUrl(String key, Duration validFor, String downloadName) {
        try {
            var args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(objectName(key))
                    .expiry((int) validFor.toSeconds());
            if (downloadName != null && !downloadName.isBlank()) {
                args.extraQueryParams(Map.of("response-content-disposition",
                        "attachment; filename=\"" + downloadName + "\""));
            }
            return client.getPresignedObjectUrl(args.build());
        } catch (Exception e) {
            log.error("Could not sign URL for key {}", key, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE");
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectName(key))
                    .build());
        } catch (Exception e) {
            log.warn("Could not delete stored object {}", key, e);
        }
    }

    private String objectName(String key) {
        return properties.sourceFolder() + key;
    }
}
