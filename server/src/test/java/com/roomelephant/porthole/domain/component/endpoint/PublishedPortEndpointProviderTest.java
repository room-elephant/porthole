package com.roomelephant.porthole.domain.component.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.config.properties.EndpointProperties;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
import com.roomelephant.porthole.domain.model.Endpoint;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublishedPortEndpointProviderTest {
    private final PublishedPortEndpointProvider provider =
            new PublishedPortEndpointProvider(new PublishedHostResolver(new EndpointProperties(null)));

    @Test
    void shouldBuildCompletePublishedPortUrl() {
        assertThat(provider.discover(
                        EndpointFixtures.container(Set.of(51413), Map.of()),
                        new ContainerContext("192.168.50.6", false, false)))
                .containsExactly(new Endpoint("published-port:51413", "published-port", "http://192.168.50.6:51413/"));
    }

    @Test
    void shouldPreservePortIdAcrossHostnames() {
        Container container = EndpointFixtures.container(Set.of(51413), Map.of());
        Endpoint first = provider.discover(container, new ContainerContext("192.168.50.6", false, false))
                .getFirst();
        Endpoint second = provider.discover(container, new ContainerContext("docker.example.com", false, false))
                .getFirst();
        assertThat(second.id()).isEqualTo("published-port:51413");
        assertThat(first.url()).isEqualTo("http://192.168.50.6:51413/");
        assertThat(second.url()).isEqualTo("http://docker.example.com:51413/");
    }

    @Test
    void shouldReturnNoCandidatesWithoutPublishedPorts() {
        assertThat(provider.discover(
                        EndpointFixtures.container(Set.of(), Map.of()),
                        new ContainerContext("localhost", false, false)))
                .isEmpty();
    }
}
