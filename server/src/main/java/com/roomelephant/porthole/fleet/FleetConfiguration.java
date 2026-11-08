package com.roomelephant.porthole.fleet;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@NullMarked
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DockerProperties.class)
public class FleetConfiguration {
    @Bean
    SourceCatalog sourceCatalog(DockerProperties properties) {
        List<Source> sources = properties.connections().stream()
                .map(connection -> new Source(
                        connection.id(),
                        connection.name(),
                        connection.publishedHost(),
                        connection.host().startsWith("unix://"),
                        connection.connectionTimeout().plus(connection.responseTimeout())))
                .toList();
        return new SourceCatalog(sources);
    }

    @Bean
    FleetSettings fleetSettings(DockerProperties properties) {
        return new FleetSettings(properties.pollInterval(), properties.snapshotRetention());
    }
}
