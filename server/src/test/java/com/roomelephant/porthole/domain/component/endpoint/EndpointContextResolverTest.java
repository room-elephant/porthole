package com.roomelephant.porthole.domain.component.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.config.properties.EndpointProperties;
import org.junit.jupiter.api.Test;

class EndpointContextResolverTest {
    @Test
    void shouldResolveOverrideBeforeRequestHost() {
        assertThat(new EndpointContextResolver(new EndpointProperties("docker.example.com"))
                        .resolve("proxy.example.com")
                        .publishedHost())
                .isEqualTo("docker.example.com");
    }

    @Test
    void shouldUseEachRequestHostnameWithoutKeepingRequestState() {
        var resolver = new EndpointContextResolver(new EndpointProperties(null));
        assertThat(resolver.resolve("192.168.50.6").publishedHost()).isEqualTo("192.168.50.6");
        assertThat(resolver.resolve("other.example.com").publishedHost()).isEqualTo("other.example.com");
    }
}
