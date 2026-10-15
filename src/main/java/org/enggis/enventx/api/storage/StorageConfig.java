package org.enggis.enventx.api.storage;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class StorageConfig {

    @Bean
    MinioClient minioClient(MinioProperties properties) {
        // an explicit region avoids a network lookup when signing URLs
        return MinioClient.builder()
                .endpoint(properties.url())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
    }

    @Bean
    FileStorage fileStorage(MinioClient client, MinioProperties properties) {
        return new MinioFileStorage(client, properties);
    }
}
