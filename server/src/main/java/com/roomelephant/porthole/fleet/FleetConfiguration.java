package com.roomelephant.porthole.fleet;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@NullMarked
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DockerProperties.class, SelfProperties.class})
public class FleetConfiguration {
    private static final Logger log = LoggerFactory.getLogger(FleetConfiguration.class);

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

    @Bean
    SelfContainer selfContainer(SelfProperties properties) {
        SelfContainer self = new SelfContainerResolver(
                        System::getenv, Path.of("/proc/self/mountinfo"), Path.of("/proc/self/cgroup"))
                .resolve(properties.selfContainerId());
        log.info("Resolved own container ID: {}", self.id() == null ? "none" : self.id());
        return self;
    }
}
