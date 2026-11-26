package com.roomelephant.porthole.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublishedHostResolverTest {
    private final PublishedHostResolver resolver = new PublishedHostResolver();

    @Test
    void usesConfiguredConnectionHost() {
        assertThat(resolver.resolve("192.168.50.5")).isEqualTo("192.168.50.5");
    }

    @Test
    void defaultsToLoopbackWhenConnectionHasNoPublishedHost() {
        assertThat(resolver.resolve(null)).isEqualTo("127.0.0.1");
    }
}
