package com.roomelephant.porthole.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class SelfPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void bindsKebabCaseProperty() {
        runner.withPropertyValues("porthole.self-container-id=abc123def456")
                .run(context -> assertThat(context.getBean(SelfProperties.class).selfContainerId())
                        .isEqualTo("abc123def456"));
    }

    @Test
    void bindsEnvironmentVariable() {
        runner.withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("PORTHOLE_SELFCONTAINERID", "abc123def456"))))
                .run(context -> assertThat(context.getBean(SelfProperties.class).selfContainerId())
                        .isEqualTo("abc123def456"));
    }

    @Test
    void defaultsToNull() {
        runner.run(context -> assertThat(context.getBean(SelfProperties.class).selfContainerId())
                .isNull());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SelfProperties.class)
    static class Config {}
}
