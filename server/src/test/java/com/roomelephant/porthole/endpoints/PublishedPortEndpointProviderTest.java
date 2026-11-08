package com.roomelephant.porthole.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublishedPortEndpointProviderTest {
    private final PublishedPortEndpointProvider provider =
            new PublishedPortEndpointProvider(new PublishedHostResolver());

    @Test
    void shouldBuildCompletePublishedPortUrl() {
        assertThat(provider.discover(
                        EndpointFixtures.at(EndpointFixtures.target(Set.of(51413), Map.of()), "192.168.50.6")))
                .containsExactly(new Candidate(
                        "published-port:51413", "published-port", "http://192.168.50.6:51413/", CandidateKind.DIRECT));
    }

    @Test
    void shouldPreservePortIdAcrossHostnames() {
        EndpointTarget container = EndpointFixtures.target(Set.of(51413), Map.of());
        Candidate first = provider.discover(EndpointFixtures.at(container, "192.168.50.6"))
                .getFirst();
        Candidate second = provider.discover(EndpointFixtures.at(container, "docker.example.com"))
                .getFirst();
        assertThat(second.id()).isEqualTo("published-port:51413");
        assertThat(first.url()).isEqualTo("http://192.168.50.6:51413/");
        assertThat(second.url()).isEqualTo("http://docker.example.com:51413/");
    }

    @Test
    void shouldReturnNoCandidatesWithoutPublishedPorts() {
        assertThat(provider.discover(EndpointFixtures.at(EndpointFixtures.target(Set.of(), Map.of()), "localhost")))
                .isEmpty();
    }
}
