package com.roomelephant.porthole.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

class RegistryPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withInitializer(context -> {
                try {
                    for (var source :
                            new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yml"))) {
                        context.getEnvironment().getPropertySources().addLast(source);
                    }
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            });

    @Test
    void bindsDockerHubDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var hub = context.getBean(RegistryProperties.class).dockerhub();
            assertThat(hub.registryUrl()).isEqualTo("https://registry-1.docker.io/v2/");
            assertThat(hub.authUrl())
                    .isEqualTo("https://auth.docker.io/token?service=registry.docker.io&scope=repository:");
            assertThat(hub.repositoriesUrl()).isEqualTo("https://hub.docker.com/v2/repositories/");
        });
    }

    @Test
    void bindsDockerHubPropertyNames() {
        runner.withPropertyValues(
                        "porthole.registry.dockerhub.registry-url=https://new-registry/",
                        "porthole.registry.dockerhub.auth-url=https://new-auth/",
                        "porthole.registry.dockerhub.repositories-url=https://new-catalogue/")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var hub = context.getBean(RegistryProperties.class).dockerhub();
                    assertThat(hub.registryUrl()).isEqualTo("https://new-registry/");
                    assertThat(hub.authUrl()).isEqualTo("https://new-auth/");
                    assertThat(hub.repositoriesUrl()).isEqualTo("https://new-catalogue/");
                });
    }

    @Test
    void bindsDockerHubEnvironmentVariables() {
        environment(Map.of(
                        "PORTHOLE_REGISTRY_DOCKERHUB_REGISTRYURL",
                        "https://new-registry/",
                        "PORTHOLE_REGISTRY_DOCKERHUB_AUTHURL",
                        "https://new-auth/",
                        "PORTHOLE_REGISTRY_DOCKERHUB_REPOSITORIESURL",
                        "https://new-catalogue/"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var hub = context.getBean(RegistryProperties.class).dockerhub();
                    assertThat(hub.registryUrl()).isEqualTo("https://new-registry/");
                    assertThat(hub.authUrl()).isEqualTo("https://new-auth/");
                    assertThat(hub.repositoriesUrl()).isEqualTo("https://new-catalogue/");
                });
    }

    @Test
    void bindsNamespacedRegistryAndIconProperties() {
        runner.withPropertyValues(
                        "porthole.registry.timeout.connect=2s",
                        "porthole.registry.timeout.read=3s",
                        "porthole.registry.cache.ttl=2h",
                        "porthole.registry.cache.version-max-size=250",
                        "porthole.registry.ghcr.registry-url=https://custom-ghcr/",
                        "porthole.registry.ghcr.auth-url=https://custom-ghcr-auth/",
                        "porthole.dashboard.icons.path=/custom/icons.yml",
                        "porthole.dashboard.icons.url=https://custom-icons/",
                        "porthole.dashboard.icons.extension=.svg")
                .run(context -> assertNamespacedOverrides(
                        context.getBean(RegistryProperties.class), context.getBean(DashboardProperties.class)));
    }

    @Test
    void bindsNamespacedRegistryAndIconEnvironmentVariables() {
        environment(Map.of(
                        "PORTHOLE_REGISTRY_TIMEOUT_CONNECT", "2s",
                        "PORTHOLE_REGISTRY_TIMEOUT_READ", "3s",
                        "PORTHOLE_REGISTRY_CACHE_TTL", "2h",
                        "PORTHOLE_REGISTRY_CACHE_VERSIONMAXSIZE", "250",
                        "PORTHOLE_REGISTRY_GHCR_REGISTRYURL", "https://custom-ghcr/",
                        "PORTHOLE_REGISTRY_GHCR_AUTHURL", "https://custom-ghcr-auth/",
                        "PORTHOLE_DASHBOARD_ICONS_PATH", "/custom/icons.yml",
                        "PORTHOLE_DASHBOARD_ICONS_URL", "https://custom-icons/",
                        "PORTHOLE_DASHBOARD_ICONS_EXTENSION", ".svg"))
                .run(context -> assertNamespacedOverrides(
                        context.getBean(RegistryProperties.class), context.getBean(DashboardProperties.class)));
    }

    private void assertNamespacedOverrides(RegistryProperties registry, DashboardProperties dashboard) {
        assertThat(registry.timeout().connect()).isEqualTo(java.time.Duration.ofSeconds(2));
        assertThat(registry.timeout().read()).isEqualTo(java.time.Duration.ofSeconds(3));
        assertThat(registry.cache().ttl()).isEqualTo(java.time.Duration.ofHours(2));
        assertThat(registry.cache().versionMaxSize()).isEqualTo(250);
        assertThat(registry.ghcr().registryUrl()).isEqualTo("https://custom-ghcr/");
        assertThat(registry.ghcr().authUrl()).isEqualTo("https://custom-ghcr-auth/");
        assertThat(dashboard.icons().path()).isEqualTo("/custom/icons.yml");
        assertThat(dashboard.icons().url()).isEqualTo("https://custom-icons/");
        assertThat(dashboard.icons().extension()).isEqualTo(".svg");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "porthole.registry.dockerhub.registry-url= ",
                "porthole.registry.dockerhub.auth-url= ",
                "porthole.registry.dockerhub.repositories-url= ",
                "porthole.registry.ghcr.registry-url= ",
                "porthole.registry.ghcr.auth-url= ",
                "porthole.registry.timeout.connect=0s",
                "porthole.registry.timeout.connect=-1s",
                "porthole.registry.timeout.read=0s",
                "porthole.registry.timeout.read=-1s",
                "porthole.registry.cache.ttl=0s",
                "porthole.registry.cache.ttl=-1s"
            })
    void rejectsInvalidRegistryConfiguration(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner environment(Map<String, Object> values) {
        return runner.withInitializer(context -> context.getEnvironment()
                .getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource("systemEnvironment", values)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({RegistryProperties.class, DashboardProperties.class})
    static class Config {}
}
