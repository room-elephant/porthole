package com.roomelephant.porthole.domain.component.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.domain.model.EndpointContext;
import com.roomelephant.porthole.domain.model.EndpointDTO;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublishedPortEndpointProviderTest {
    private final PublishedPortEndpointProvider provider = new PublishedPortEndpointProvider();

    @Test
    void shouldBuildCompletePublishedPortUrl() {
        assertThat(provider.discover(
                        EndpointFixtures.container(Set.of(51413), Map.of()), new EndpointContext("192.168.50.6")))
                .containsExactly(
                        new EndpointDTO("published-port:51413", "published-port", "http://192.168.50.6:51413/"));
    }

    @Test
    void shouldPreservePortIdAcrossHostnames() {
        var container = EndpointFixtures.container(Set.of(51413), Map.of());
        var first = provider.discover(container, new EndpointContext("192.168.50.6"))
                .getFirst();
        var second = provider.discover(container, new EndpointContext("docker.example.com"))
                .getFirst();
        assertThat(second.id()).isEqualTo("published-port:51413");
        assertThat(first.url()).isEqualTo("http://192.168.50.6:51413/");
        assertThat(second.url()).isEqualTo("http://docker.example.com:51413/");
    }

    @Test
    void shouldReturnNoCandidatesWithoutPublishedPorts() {
        assertThat(provider.discover(EndpointFixtures.container(Set.of(), Map.of()), new EndpointContext("localhost")))
                .isEmpty();
    }
}
