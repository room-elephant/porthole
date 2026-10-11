package com.roomelephant.porthole.domain.component.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.domain.model.Endpoint;
import com.roomelephant.porthole.domain.model.EndpointContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class EndpointDiscoveryTest {
    @Test
    void shouldInjectEveryRegisteredProviderBean() {
        try (var context = new AnnotationConfigApplicationContext(
                EndpointDiscovery.class,
                PublishedPortEndpointProvider.class,
                TraefikEndpointProvider.class,
                TraefikRuleParser.class)) {
            assertThat(context.getBean(EndpointDiscovery.class)
                            .discover(
                                    EndpointFixtures.container(
                                            Set.of(51413),
                                            Map.of(
                                                    "traefik.http.routers.app.rule", "Host(`app.example.com`)",
                                                    "traefik.http.routers.app.tls", "true")),
                                    new EndpointContext("192.168.50.6")))
                    .containsExactly(
                            new Endpoint("published-port:51413", "published-port", "http://192.168.50.6:51413/"),
                            new Endpoint("traefik:https://app.example.com/", "traefik", "https://app.example.com/"));
        }
    }

    @Test
    void shouldDeduplicateAndSortWithoutSelectingDefault() {
        var proxy = new TraefikEndpointProvider(new TraefikRuleParser());
        var container = EndpointFixtures.container(
                Set.of(),
                Map.of(
                        "traefik.http.routers.root.rule", "Host(`app.example.com`)",
                        "traefik.http.routers.root.tls", "true",
                        "traefik.http.routers.path.rule", "Host(`app.example.com`) && PathPrefix(`/z/`)",
                        "traefik.http.routers.path.tls", "true"));
        assertThat(new EndpointDiscovery(List.of(proxy, proxy, new PublishedPortEndpointProvider()))
                        .discover(container, new EndpointContext("localhost")))
                .extracting(Endpoint::url)
                .containsExactly("https://app.example.com/", "https://app.example.com/z/");
    }
}
