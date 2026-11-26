package com.roomelephant.porthole.entries;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class EntriesPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    @Test
    void defaultsToDataDirectory() {
        runner.run(context -> {
            EntriesProperties properties = context.getBean(EntriesProperties.class);
            assertThat(properties.dir()).isEqualTo(Path.of("data"));
            assertThat(properties.iconSweepInterval()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.unreferencedIconAge()).isEqualTo(Duration.ofHours(1));
        });
    }

    @Test
    void bindsPortholeDataDirEnvironmentVariable() {
        runner.withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("PORTHOLE_DATA_DIR", "/app/data"))))
                .run(context -> assertThat(
                                context.getBean(EntriesProperties.class).dir())
                        .isEqualTo(Path.of("/app/data")));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EntriesProperties.class)
    static class Config {}
}
