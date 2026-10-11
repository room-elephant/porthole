package com.roomelephant.porthole.domain.component.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.domain.model.EndpointContext;
import com.roomelephant.porthole.domain.model.EndpointDTO;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class EndpointDiscoveryTest {
    @Test
    void shouldInjectEveryProviderBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(EndpointDiscovery.class, PublishedPortEndpointProvider.class);
            context.registerBean(
                    "additionalProvider",
                    EndpointProvider.class,
                    () -> (container, endpointContext) ->
                            List.of(new EndpointDTO("ngnext:https://example.com/", "ngnext", "https://example.com/")));
            context.refresh();
            assertThat(context.getBean(EndpointDiscovery.class)
                            .discover(
                                    EndpointFixtures.container(Set.of(51413), Map.of()),
                                    new EndpointContext("192.168.50.6")))
                    .containsExactly(
                            new EndpointDTO("ngnext:https://example.com/", "ngnext", "https://example.com/"),
                            new EndpointDTO("published-port:51413", "published-port", "http://192.168.50.6:51413/"));
        }
    }

    @Test
    void shouldDeduplicateAndSortWithoutSelectingDefault() {
        EndpointProvider first = (container, context) -> List.of(
                new EndpointDTO("traefik:https://example.com/z/", "traefik", "https://example.com/z/"),
                new EndpointDTO("traefik:https://example.com/", "traefik", "https://example.com/"));
        EndpointProvider duplicate = (container, context) ->
                List.of(new EndpointDTO("traefik:https://example.com/", "traefik", "https://example.com/"));
        EndpointProvider empty = (container, context) -> List.of();
        assertThat(new EndpointDiscovery(List.of(first, duplicate, empty))
                        .discover(EndpointFixtures.container(Set.of(), Map.of()), new EndpointContext("localhost")))
                .extracting(EndpointDTO::url)
                .containsExactly("https://example.com/", "https://example.com/z/");
    }
}
