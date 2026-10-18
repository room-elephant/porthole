package com.roomelephant.porthole.core.enrichment.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import org.junit.jupiter.api.Test;

class PublishedHostResolverTest {
    private final PublishedHostResolver resolver = new PublishedHostResolver();

    @Test
    void connectionHostOverridesRequestHost() {
        assertThat(resolver.resolve(new EnrichmentContext("local", "proxy.example.com", "192.168.50.5", false, false)))
                .isEqualTo("192.168.50.5");
    }

    @Test
    void usesEachRequestHostnameWhenConnectionHasNoPublishedHost() {
        assertThat(resolver.resolve(new EnrichmentContext("local", "192.168.50.6", null, false, false)))
                .isEqualTo("192.168.50.6");
        assertThat(resolver.resolve(new EnrichmentContext("local", "other.example.com", null, false, false)))
                .isEqualTo("other.example.com");
    }
}
