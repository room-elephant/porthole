package com.roomelephant.porthole.core.enrichment.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.Endpoint;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublishedPortEndpointProviderTest {
    private final PublishedPortEndpointProvider provider =
            new PublishedPortEndpointProvider(new PublishedHostResolver());

    @Test
    void shouldBuildCompletePublishedPortUrl() {
        assertThat(provider.discover(
                        EndpointFixtures.container(Set.of(51413), Map.of()),
                        new EnrichmentContext("local", "192.168.50.6", false, false, java.util.Map.of())))
                .containsExactly(new Endpoint("published-port:51413", "published-port", "http://192.168.50.6:51413/"));
    }

    @Test
    void shouldPreservePortIdAcrossHostnames() {
        Container container = EndpointFixtures.container(Set.of(51413), Map.of());
        Endpoint first = provider.discover(
                        container, new EnrichmentContext("local", "192.168.50.6", false, false, java.util.Map.of()))
                .getFirst();
        Endpoint second = provider.discover(
                        container,
                        new EnrichmentContext("local", "docker.example.com", false, false, java.util.Map.of()))
                .getFirst();
        assertThat(second.id()).isEqualTo("published-port:51413");
        assertThat(first.url()).isEqualTo("http://192.168.50.6:51413/");
        assertThat(second.url()).isEqualTo("http://docker.example.com:51413/");
    }

    @Test
    void shouldReturnNoCandidatesWithoutPublishedPorts() {
        assertThat(provider.discover(
                        EndpointFixtures.container(Set.of(), Map.of()),
                        new EnrichmentContext("local", "localhost", false, false, java.util.Map.of())))
                .isEmpty();
    }
}
