package com.roomelephant.porthole.core.enrichment.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EndpointUrlsTest {
    @Test
    void shouldFormatIpv6Url() {
        assertThat(EndpointUrls.publishedPortUrl("2001:db8::6", 51413)).isEqualTo("http://[2001:db8::6]:51413/");
        assertThat(EndpointUrls.publishedPortUrl("[2001:db8::6]", 51413)).isEqualTo("http://[2001:db8::6]:51413/");
    }

    @Test
    void shouldNormalizeRouteUrl() {
        assertThat(EndpointUrls.routeUrl("https", "EXAMPLE.COM", "")).isEqualTo("https://example.com/");
        assertThat(EndpointUrls.routeUrl("https", "EXAMPLE.COM", "/DL/")).isEqualTo("https://example.com/DL/");
    }

    @Test
    void shouldKeepHttpForPublishedPorts() {
        assertThat(EndpointUrls.publishedPortUrl("docker.example.com", 443))
                .isEqualTo("http://docker.example.com:443/");
    }
}
