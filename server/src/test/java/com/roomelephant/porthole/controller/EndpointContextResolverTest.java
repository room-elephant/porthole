package com.roomelephant.porthole.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.config.properties.EndpointProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class EndpointContextResolverTest {
    @Test
    void shouldResolveOverrideBeforeRequestHost() {
        var request = new MockHttpServletRequest();
        request.setServerName("proxy.example.com");
        assertThat(new EndpointContextResolver(new EndpointProperties("docker.example.com"))
                        .resolve(request)
                        .publishedHost())
                .isEqualTo("docker.example.com");
    }

    @Test
    void shouldUseRequestServerNameWithoutItsSchemeOrPort() {
        var resolver = new EndpointContextResolver(new EndpointProperties(null));
        var request = new MockHttpServletRequest();
        request.setServerName("192.168.50.6");
        request.setScheme("https");
        request.setServerPort(9753);
        request.setRemoteAddr("192.168.50.99");
        request.addHeader("X-Forwarded-Host", "ignored.example.com");
        assertThat(resolver.resolve(request).publishedHost()).isEqualTo("192.168.50.6");
        request.setServerName("other.example.com");
        assertThat(resolver.resolve(request).publishedHost()).isEqualTo("other.example.com");
    }
}
