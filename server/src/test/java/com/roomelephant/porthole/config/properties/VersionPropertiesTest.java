package com.roomelephant.porthole.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class VersionPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @ParameterizedTest
    @ValueSource(strings = {"1h", "250ms"})
    void bindsPositiveIntervals(String value) {
        runner.withPropertyValues("porthole.versions.refresh-interval=" + value).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(VersionProperties.class).refreshInterval())
                    .isEqualTo(value.equals("1h") ? Duration.ofHours(1) : Duration.ofMillis(250));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1h"})
    void rejectsNonPositiveIntervals(String value) {
        runner.withPropertyValues("porthole.versions.refresh-interval=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsIntervalsThatOverflowNanoseconds() {
        runner.withPropertyValues("porthole.versions.refresh-interval=106752d")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingInterval() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(VersionProperties.class)
    static class Config {}
}
