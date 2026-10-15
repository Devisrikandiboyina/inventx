package org.enggis.enventx.api.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioFileStorageTest {

    @Test
    void signsShortLivedUrlsOfflineUnderTheSourceFolder() {
        var props = new MinioProperties("https://minio.example.test", "ak", "secret-key-123", "bucket", "inventx", null);
        assertEquals("inventx/", props.sourceFolder());
        assertEquals("us-east-1", props.region());
        var client = MinioClient.builder().endpoint(props.url()).credentials("ak", "secret-key-123")
                .region(props.region()).build();
        var storage = new MinioFileStorage(client, props);

        var url = storage.presignedGetUrl("ideas/1/attachments/2", Duration.ofMinutes(5), "plan.pdf");
        var uri = URI.create(url);

        assertEquals("minio.example.test", uri.getHost());
        assertEquals("/bucket/inventx/ideas/1/attachments/2", uri.getPath());
        assertTrue(url.contains("X-Amz-Expires=300"));
        assertTrue(url.contains("X-Amz-Signature="));
        assertTrue(url.contains("response-content-disposition="));
        assertTrue(!url.contains("secret-key-123"));
    }
}
