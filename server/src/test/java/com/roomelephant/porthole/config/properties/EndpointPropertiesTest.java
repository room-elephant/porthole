package com.roomelephant.porthole.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class EndpointPropertiesTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @ParameterizedTest
    @ValueSource(strings = {"http://192.168.50.6", "host:9753", "host/path", "", "host?query", "user@host", "${HOST}"})
    void shouldRejectConfiguredUrlInsteadOfHostname(String host) {
        runner.withPropertyValues("porthole.endpoints.published-host=" + host)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"docker.example.com", "192.168.50.6", "2001:db8::6", "[2001:db8::6]"})
    void shouldAcceptValidPublishedHosts(String host) {
        runner.withPropertyValues("porthole.endpoints.published-host=" + host).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(EndpointProperties.class).publishedHost())
                    .isEqualTo(host);
        });
    }

    @Test
    void shouldAllowAbsentOverride() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(EndpointProperties.class).publishedHost())
                    .isNull();
        });
    }

    @Test
    void shouldBindPublishedHostEnvironmentVariable() {
        runner.withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                "test-systemEnvironment", Map.of("PORTHOLE_ENDPOINTS_PUBLISHEDHOST", "192.168.50.6"))))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EndpointProperties.class).publishedHost())
                            .isEqualTo("192.168.50.6");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EndpointProperties.class)
    static class PropertiesConfig {}
}
