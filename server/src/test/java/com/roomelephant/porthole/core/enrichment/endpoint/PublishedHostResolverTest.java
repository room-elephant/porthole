package com.roomelephant.porthole.core.enrichment.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import org.junit.jupiter.api.Test;

class PublishedHostResolverTest {
    private final PublishedHostResolver resolver = new PublishedHostResolver();

    @Test
    void usesConfiguredConnectionHost() {
        assertThat(resolver.resolve(new EnrichmentContext("local", "192.168.50.5", false, false)))
                .isEqualTo("192.168.50.5");
    }

    @Test
    void defaultsToLoopbackWhenConnectionHasNoPublishedHost() {
        assertThat(resolver.resolve(new EnrichmentContext("local", null, false, false)))
                .isEqualTo("127.0.0.1");
    }
}
