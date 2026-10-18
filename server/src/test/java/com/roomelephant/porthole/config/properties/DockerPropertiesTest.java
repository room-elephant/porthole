package com.roomelephant.porthole.config.properties;

import static org.assertj.core.api.Assertions.*;

import com.roomelephant.porthole.config.DockerConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class DockerPropertiesTest {
    @ParameterizedTest
    @ValueSource(strings = {"http://192.168.50.6", "host:9753", "host/path", "", "host?query", "user@host", "${HOST}"})
    void rejectsInvalidConnectionPublishedHosts(String host) {
        source().withPropertyValues("porthole.docker.connections[0].published-host=" + host)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"docker.example.com", "192.168.50.6", "2001:db8::6", "[2001:db8::6]"})
    void acceptsValidConnectionPublishedHosts(String host) {
        source().withPropertyValues("porthole.docker.connections[0].published-host=" + host)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DockerProperties.class)
                                    .connections()
                                    .getFirst()
                                    .publishedHost())
                            .isEqualTo(host);
                });
    }

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

    private ApplicationContextRunner layered(String yaml) {
        return runner.withUserConfiguration(DockerConfig.class).withInitializer(c -> {
            var loader = new YamlPropertySourceLoader();
            try {
                for (var source : loader.load("packaged", new ClassPathResource("application.yml")))
                    c.getEnvironment().getPropertySources().addLast(source);
                for (var source : loader.load(
                        "mounted", new ByteArrayResource(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                    c.getEnvironment().getPropertySources().addFirst(source);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void realLegacyEnvironmentAndSystemPropertiesFailWithMigrationGuidance() {
        for (String key : new String[] {"host", "connection-timeout", "response-timeout"}) {
            String value = key.equals("host") ? "unix:///old.sock" : "3s";
            String envKey = "PORTHOLE_DOCKER_" + key.replace("-", "").toUpperCase(java.util.Locale.ROOT);
            layered("{}")
                    .withInitializer(c -> c.getEnvironment()
                            .getPropertySources()
                            .replace(
                                    "systemEnvironment",
                                    new SystemEnvironmentPropertySource("systemEnvironment", Map.of(envKey, value))))
                    .run(c -> assertThat(c)
                            .hasFailed()
                            .getFailure()
                            .hasStackTraceContaining("porthole.docker.connections"));
            layered("{}")
                    .withInitializer(c -> c.getEnvironment()
                            .getPropertySources()
                            .replace(
                                    "systemProperties",
                                    new MapPropertySource("systemProperties", Map.of("porthole.docker." + key, value))))
                    .run(c -> assertThat(c)
                            .hasFailed()
                            .getFailure()
                            .hasStackTraceContaining("porthole.docker.connections"));
        }
    }

    @Test
    void mountedListReplacesPackagedConnectionEntirely() {
        layered("""
                porthole.docker.connections:
                  - id: mike
                    name: Mike
                    host: tcp://192.168.50.5:2375
                """).run(c -> {
            assertThat(c).hasNotFailed();
            var connections = c.getBean(DockerProperties.class).connections();
            assertThat(connections).hasSize(1);
            assertThat(connections.getFirst().id()).isEqualTo("mike");
            assertThat(connections.getFirst().host()).isEqualTo("tcp://192.168.50.5:2375");
        });
    }

    @Test
    void incompleteOrMalformedMountedListCannotInheritPackagedFields() {
        for (String yaml : new String[] {
            "porthole.docker.connections: []",
            "porthole.docker.connections: [{id: mike, name: Mike}]",
            "porthole.docker.connections: [{id: mike, host: 'unix:///mike.sock'}]",
            "porthole.docker.connections: [{name: Mike, host: 'unix:///mike.sock'}]",
            "porthole.docker.connections: [{id: mike, name: Mike, host: 'unix:///mike.sock', response-timeout: invalid}]"
        }) layered(yaml).run(c -> assertThat(c).hasFailed());
    }

    @Test
    void indexedEnvironmentListReplacesPackagedDefaults() {
        layered("{}")
                .withInitializer(c -> c.getEnvironment()
                        .getPropertySources()
                        .replace(
                                "systemEnvironment",
                                new SystemEnvironmentPropertySource(
                                        "systemEnvironment",
                                        Map.of(
                                                "PORTHOLE_DOCKER_CONNECTIONS_0_ID", "mike",
                                                "PORTHOLE_DOCKER_CONNECTIONS_0_NAME", "Mike",
                                                "PORTHOLE_DOCKER_CONNECTIONS_0_HOST", "unix:///mike.sock",
                                                "PORTHOLE_DOCKER_CONNECTIONS_0_RESPONSETIMEOUT", "9s"))))
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    var connections = c.getBean(DockerProperties.class).connections();
                    assertThat(connections).hasSize(1);
                    assertThat(connections.getFirst().id()).isEqualTo("mike");
                    assertThat(connections.getFirst().responseTimeout()).isEqualTo(java.time.Duration.ofSeconds(9));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DockerProperties.class)
    static class Config {}
}
