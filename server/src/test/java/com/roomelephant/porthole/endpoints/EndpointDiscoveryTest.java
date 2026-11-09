package com.roomelephant.porthole.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

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
                            .discover(EndpointFixtures.target(
                                    Set.of(51413),
                                    Map.of(
                                            "traefik.http.routers.app.rule", "Host(`app.example.com`)",
                                            "traefik.http.routers.app.tls", "true"))))
                    .containsExactly(
                            new Candidate(
                                    "published-port:51413",
                                    "published-port",
                                    "http://192.168.50.6:51413/",
                                    CandidateKind.DIRECT),
                            new Candidate(
                                    "traefik:https://app.example.com/",
                                    "traefik",
                                    "https://app.example.com/",
                                    CandidateKind.ROUTED));
        }
    }

    @Test
    void shouldDeduplicateAndSortWithoutSelectingDefault() {
        TraefikEndpointProvider proxy = new TraefikEndpointProvider(new TraefikRuleParser());
        EndpointTarget container = EndpointFixtures.target(
                Set.of(),
                Map.of(
                        "traefik.http.routers.root.rule", "Host(`app.example.com`)",
                        "traefik.http.routers.root.tls", "true",
                        "traefik.http.routers.path.rule", "Host(`app.example.com`) && PathPrefix(`/z/`)",
                        "traefik.http.routers.path.tls", "true"));
        assertThat(new EndpointDiscovery(
                                List.of(proxy, proxy, new PublishedPortEndpointProvider(new PublishedHostResolver())))
                        .discover(EndpointFixtures.at(container, "localhost")))
                .extracting(Candidate::url)
                .containsExactly("https://app.example.com/", "https://app.example.com/z/");
    }
}
