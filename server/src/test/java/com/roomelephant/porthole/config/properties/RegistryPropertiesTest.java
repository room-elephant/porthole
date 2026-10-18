package com.roomelephant.porthole.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
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
    void bindsNewDockerHubPropertyNames() {
        runner.withPropertyValues(
                        "registry.dockerhub.registry-url=https://new-registry/",
                        "registry.dockerhub.auth-url=https://new-auth/",
                        "registry.dockerhub.repositories-url=https://new-catalogue/")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var hub = context.getBean(RegistryProperties.class).dockerhub();
                    assertThat(hub.registryUrl()).isEqualTo("https://new-registry/");
                    assertThat(hub.authUrl()).isEqualTo("https://new-auth/");
                    assertThat(hub.repositoriesUrl()).isEqualTo("https://new-catalogue/");
                });
    }

    @Test
    void retainsLegacyPropertyOverrides() {
        runner.withPropertyValues(
                        "registry.urls.registry=https://old-registry/",
                        "registry.urls.auth=https://old-auth/",
                        "registry.urls.repositories=https://old-catalogue/")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var hub = context.getBean(RegistryProperties.class).dockerhub();
                    assertThat(hub.registryUrl()).isEqualTo("https://old-registry/");
                    assertThat(hub.authUrl()).isEqualTo("https://old-auth/");
                    assertThat(hub.repositoriesUrl()).isEqualTo("https://old-catalogue/");
                });
    }

    @Test
    void newPropertiesWinPerSettingAndUnspecifiedSettingsUseLegacyFallbacks() {
        runner.withPropertyValues(
                        "registry.dockerhub.registry-url=https://new-registry/",
                        "registry.urls.registry=https://old-registry/",
                        "registry.urls.auth=https://old-auth/",
                        "registry.urls.repositories=https://old-catalogue/")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var hub = context.getBean(RegistryProperties.class).dockerhub();
                    assertThat(hub.registryUrl()).isEqualTo("https://new-registry/");
                    assertThat(hub.authUrl()).isEqualTo("https://old-auth/");
                    assertThat(hub.repositoriesUrl()).isEqualTo("https://old-catalogue/");
                });
    }

    @Test
    void bindsNewDockerHubEnvironmentVariables() {
        environment(Map.of(
                        "REGISTRY_DOCKERHUB_REGISTRY_URL",
                        "https://new-registry/",
                        "REGISTRY_DOCKERHUB_AUTH_URL",
                        "https://new-auth/",
                        "REGISTRY_DOCKERHUB_REPOSITORIES_URL",
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
    void retainsLegacyEnvironmentVariables() {
        environment(Map.of(
                        "REGISTRY_URLS_REGISTRY",
                        "https://old-registry/",
                        "REGISTRY_URLS_AUTH",
                        "https://old-auth/",
                        "REGISTRY_URLS_REPOSITORIES",
                        "https://old-catalogue/"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var hub = context.getBean(RegistryProperties.class).dockerhub();
                    assertThat(hub.registryUrl()).isEqualTo("https://old-registry/");
                    assertThat(hub.authUrl()).isEqualTo("https://old-auth/");
                    assertThat(hub.repositoriesUrl()).isEqualTo("https://old-catalogue/");
                });
    }

    @Test
    void newEnvironmentOverridesLegacyEnvironment() {
        environment(Map.of(
                        "REGISTRY_DOCKERHUB_REGISTRY_URL",
                        "https://new-registry/",
                        "REGISTRY_URLS_REGISTRY",
                        "https://old-registry/",
                        "REGISTRY_DOCKERHUB_AUTH_URL",
                        "https://new-auth/",
                        "REGISTRY_URLS_AUTH",
                        "https://old-auth/",
                        "REGISTRY_DOCKERHUB_REPOSITORIES_URL",
                        "https://new-catalogue/",
                        "REGISTRY_URLS_REPOSITORIES",
                        "https://old-catalogue/"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var hub = context.getBean(RegistryProperties.class).dockerhub();
                    assertThat(hub.registryUrl()).isEqualTo("https://new-registry/");
                    assertThat(hub.authUrl()).isEqualTo("https://new-auth/");
                    assertThat(hub.repositoriesUrl()).isEqualTo("https://new-catalogue/");
                });
    }

    private ApplicationContextRunner environment(Map<String, Object> values) {
        return runner.withInitializer(context -> context.getEnvironment()
                .getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource("test-env", values)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RegistryProperties.class)
    static class Config {}
}
