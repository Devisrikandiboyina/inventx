package org.enggis.enventx.api.scan;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClamAvProperties.class)
public class ScanConfig {

    @Bean
    VirusScanner virusScanner(ClamAvProperties properties) {
        return properties.enabled()
                ? new ClamAvScanner(properties.host(), properties.port(), properties.timeoutMs())
                : VirusScanner.disabled();
    }
}