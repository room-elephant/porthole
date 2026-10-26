package com.roomelephant.porthole.core.enrichment.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.Endpoint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class EndpointDiscoveryTest {
    @Test
    void shouldInjectEveryRegisteredProviderBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(
                    EndpointDiscovery.class,
                    PublishedPortEndpointProvider.class,
                    PublishedHostResolver.class,
                    TraefikEndpointProvider.class,
                    TraefikRuleParser.class);
            context.refresh();
            assertThat(context.getBean(EndpointDiscovery.class)
                            .discover(
                                    EndpointFixtures.container(
                                            Set.of(51413),
                                            Map.of(
                                                    "traefik.http.routers.app.rule", "Host(`app.example.com`)",
                                                    "traefik.http.routers.app.tls", "true")),
                                    new EnrichmentContext("local", "192.168.50.6", false, false)))
                    .containsExactly(
                            new Endpoint("published-port:51413", "published-port", "http://192.168.50.6:51413/"),
                            new Endpoint("traefik:https://app.example.com/", "traefik", "https://app.example.com/"));
        }
    }

    @Test
    void shouldDeduplicateAndSortWithoutSelectingDefault() {
        TraefikEndpointProvider proxy = new TraefikEndpointProvider(new TraefikRuleParser());
        Container container = EndpointFixtures.container(
                Set.of(),
                Map.of(
                        "traefik.http.routers.root.rule", "Host(`app.example.com`)",
                        "traefik.http.routers.root.tls", "true",
                        "traefik.http.routers.path.rule", "Host(`app.example.com`) && PathPrefix(`/z/`)",
                        "traefik.http.routers.path.tls", "true"));
        assertThat(new EndpointDiscovery(
                                List.of(proxy, proxy, new PublishedPortEndpointProvider(new PublishedHostResolver())))
                        .discover(container, new EnrichmentContext("local", "localhost", false, false)))
                .extracting(Endpoint::url)
                .containsExactly("https://app.example.com/", "https://app.example.com/z/");
    }
}
