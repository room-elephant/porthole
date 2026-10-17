package com.roomelephant.porthole.config.properties;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class DockerPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    private ApplicationContextRunner source() {
        return runner.withPropertyValues(
                "porthole.docker.connections[0].id=walker",
                "porthole.docker.connections[0].name=Walker",
                "porthole.docker.connections[0].host=unix:///var/run/docker.sock");
    }

    @Test
    void defaultsTimeoutsOnEveryEntry() {
        source().run(c -> {
            assertThat(c).hasNotFailed();
            var connection = c.getBean(DockerProperties.class).connections().getFirst();
            assertThat(connection.id()).isEqualTo("walker");
            assertThat(connection.connectionTimeout()).isEqualTo(java.time.Duration.ofSeconds(5));
            assertThat(connection.responseTimeout()).isEqualTo(java.time.Duration.ofSeconds(60));
        });
    }

    @Test
    void rejectsDuplicateIdsAndMissingPublishedHosts() {
        source().withPropertyValues(
                        "porthole.docker.connections[1].id=walker",
                        "porthole.docker.connections[1].name=Other",
                        "porthole.docker.connections[1].host=unix:///other.sock")
                .run(c -> assertThat(c).hasFailed());
    }

    @Test
    void rejectsInvalidSettings() {
        for (String setting : new String[] {
            "id=bad id",
            "name=",
            "host=ssh://mike",
            "host=not-a-uri",
            "connection-timeout=0s",
            "response-timeout=-1s",
            "published-host=http://mike"
        }) {
            source().withPropertyValues("porthole.docker.connections[0]." + setting)
                    .run(c -> assertThat(c).hasFailed());
        }
    }

    @Test
    void rejectsLegacySingularSettings() {
        source().withPropertyValues("porthole.docker.host=unix:///old.sock")
                .run(c -> assertThat(c).hasFailed());
    }

    @Test
    void indexedEnvironmentVariablesBindTwoConnections() {
        runner.withInitializer(c -> c.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                "test-systemEnvironment",
                                Map.of(
                                        "PORTHOLE_DOCKER_CONNECTIONS_0_ID",
                                        "walker",
                                        "PORTHOLE_DOCKER_CONNECTIONS_0_NAME",
                                        "Walker",
                                        "PORTHOLE_DOCKER_CONNECTIONS_0_HOST",
                                        "unix:///walker.sock",
                                        "PORTHOLE_DOCKER_CONNECTIONS_0_PUBLISHEDHOST",
                                        "192.168.50.6",
                                        "PORTHOLE_DOCKER_CONNECTIONS_1_ID",
                                        "mike",
                                        "PORTHOLE_DOCKER_CONNECTIONS_1_NAME",
                                        "Mike",
                                        "PORTHOLE_DOCKER_CONNECTIONS_1_HOST",
                                        "tcp://192.168.50.5:2375",
                                        "PORTHOLE_DOCKER_CONNECTIONS_1_PUBLISHEDHOST",
                                        "192.168.50.5"))))
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c.getBean(DockerProperties.class).connections())
                            .extracting(DockerConnectionProperties::id)
                            .containsExactly("walker", "mike");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DockerProperties.class)
    static class Config {}
}
