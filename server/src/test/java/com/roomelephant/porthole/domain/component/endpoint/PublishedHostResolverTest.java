package com.roomelephant.porthole.domain.component.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.config.properties.EndpointProperties;
import org.junit.jupiter.api.Test;

class PublishedHostResolverTest {
    @Test
    void sourceHostOverridesGlobalAndRequestHost() {
        var resolver = new PublishedHostResolver(new EndpointProperties("walker.example.com"));
        assertThat(resolver.resolve(new com.roomelephant.porthole.domain.model.ContainerContext(
                        "mike", "proxy.example.com", "192.168.50.5", false, false)))
                .isEqualTo("192.168.50.5");
    }

    @Test
    void shouldResolveOverrideBeforeRequestHost() {
        assertThat(new PublishedHostResolver(new EndpointProperties("docker.example.com")).resolve("proxy.example.com"))
                .isEqualTo("docker.example.com");
    }

    @Test
    void shouldUseEachRequestHostnameWithoutKeepingRequestState() {
        PublishedHostResolver resolver = new PublishedHostResolver(new EndpointProperties(null));
        assertThat(resolver.resolve("192.168.50.6")).isEqualTo("192.168.50.6");
        assertThat(resolver.resolve("other.example.com")).isEqualTo("other.example.com");
    }
}
