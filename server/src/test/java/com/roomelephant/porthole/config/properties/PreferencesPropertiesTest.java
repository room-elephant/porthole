package com.roomelephant.porthole.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class PreferencesPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsToLocalDataDirectory() {
        runner.run(context -> assertThat(
                        context.getBean(PreferencesProperties.class).path())
                .isEqualTo(Path.of("data/preferences.json")));
    }

    @Test
    void bindsConfiguredPath() {
        runner.withPropertyValues("porthole.preferences.path=/tmp/preferences.json")
                .run(context -> assertThat(
                                context.getBean(PreferencesProperties.class).path())
                        .isEqualTo(Path.of("/tmp/preferences.json")));
    }

    @Test
    void bindsDockerEnvironmentVariable() {
        runner.withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("PORTHOLE_PREFERENCES_PATH", "/app/data/preferences.json"))))
                .run(context -> assertThat(
                                context.getBean(PreferencesProperties.class).path())
                        .isEqualTo(Path.of("/app/data/preferences.json")));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PreferencesProperties.class)
    static class Config {}
}
