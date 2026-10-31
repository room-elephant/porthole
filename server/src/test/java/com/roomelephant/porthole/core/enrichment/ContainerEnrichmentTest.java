package com.roomelephant.porthole.core.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.endpoint.*;
import com.roomelephant.porthole.core.enrichment.endpointavailability.*;
import com.roomelephant.porthole.core.enrichment.state.*;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.Endpoint;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ContainerEnrichmentTest {
    @Test
    void shouldPassEnrichedOutputToTheNextHandler() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            Container raw = container("running", Set.of(8080), Map.of("custom.metadata", "retained"));
            Container enriched = context.getBean(ContainerEnrichment.class)
                    .enrich(List.of(raw), new EnrichmentContext("local", "192.168.50.6", false, false))
                    .getFirst();
            assertThat(enriched.urlCandidates())
                    .containsExactly(
                            new Endpoint("published-port:8080", "published-port", "http://192.168.50.6:8080/", false),
                            new Endpoint("later", "later", "http://192.168.50.6:8080/later", false));
            assertThat(enriched.labels()).containsEntry("custom.metadata", "retained");
            assertThat(enriched.publishedPorts()).containsExactly(8080);
            assertThat(enriched).isSameAs(raw);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"created", "exited", "dead", "removing"})
    void shouldRejectStoppedContainersBeforeUrlDiscoveryOrLaterHandlers(String state) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            Container raw = container(state, Set.of(8080), Map.of());
            assertThat(context.getBean(ContainerEnrichment.class)
                            .enrich(List.of(raw), new EnrichmentContext("local", "localhost", true, false)))
                    .isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"running", "paused", "restarting"})
    @NullSource
    void shouldKeepActiveOrUnknownStateContainers(String state) {
        ContainerStateFilter filter = new ContainerStateFilter();
        Container raw = container(state, Set.of(8080), Map.of());
        assertThat(filter.enrich(List.of(raw), new EnrichmentContext("local", "localhost", false, false)))
                .containsExactly(raw);
    }

    @Test
    void shouldIncludeStoppedContainersWhenRequested() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            Container raw = container("exited", Set.of(8080), Map.of());
            assertThat(context.getBean(ContainerEnrichment.class)
                            .enrich(List.of(raw), new EnrichmentContext("local", "localhost", false, true)))
                    .containsExactly(raw);
        }
    }

    @Test
    void shouldKeepLabelOnlyContainersWhenEndpointsAreRequired() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            Container raw =
                    container("running", Set.of(), Map.of("traefik.http.routers.app.rule", "Host(`app.example.com`)"));
            Container enriched = context.getBean(ContainerEnrichment.class)
                    .enrich(List.of(raw), new EnrichmentContext("local", "localhost", false, false))
                    .getFirst();
            assertThat(enriched.urlCandidates())
                    .extracting(Endpoint::url)
                    .containsExactly("http://app.example.com/", "http://app.example.com/later");
        }
    }

    @Test
    void shouldRejectContainersWithoutUrlsBeforeLaterHandlers() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            assertThat(context.getBean(ContainerEnrichment.class)
                            .enrich(
                                    List.of(container("running", Set.of(), Map.of())),
                                    new EnrichmentContext("local", "localhost", false, false)))
                    .isEmpty();
        }
    }

    @Test
    void shouldIncludeContainersWithoutUrlsWhenRequested() {
        ContainerEnrichment chain = new ContainerEnrichment(List.of(new EndpointAvailabilityFilter()));
        Container raw = container("running", Set.of(), Map.of());
        assertThat(chain.enrich(List.of(raw), new EnrichmentContext("local", "localhost", true, false)))
                .containsExactly(raw);
    }

    @Test
    void shouldKeepCustomCandidatesWithoutConsultingStoredPreferences() {
        var raw = container("running", Set.of(), Map.of());
        raw.setUrlCandidates(List.of(new Endpoint("custom", "custom", "https://app.example.com", true)));

        assertThat(new EndpointAvailabilityFilter()
                        .enrich(List.of(raw), new EnrichmentContext("local", "localhost", false, false)))
                .containsExactly(raw);
    }

    @Test
    void shouldRejectEmptyResolvedCandidatesEvenWithSavedCustomMetadata() {
        var raw = container("running", Set.of(), Map.of());
        raw.setPreferences(
                new ContainerPreferences(new SelectedEndpoint("custom", "custom", "https://app.example.com")));

        assertThat(new EndpointAvailabilityFilter()
                        .enrich(List.of(raw), new EnrichmentContext("local", "localhost", false, false)))
                .isEmpty();
    }

    @Test
    void shouldKeepDiscoveredContainersWithExplicitNoWebUiSelection() {
        var raw = container("running", Set.of(), Map.of());
        raw.setPreferences(new ContainerPreferences(new SelectedEndpoint("none", "none", null)));
        raw.setUrlCandidates(
                List.of(new Endpoint("published-port:8080", "published-port", "http://localhost:8080/", false)));

        assertThat(new EndpointAvailabilityFilter()
                        .enrich(List.of(raw), new EnrichmentContext("local", "localhost", false, false)))
                .containsExactly(raw);
        assertThat(raw.preferences().selectedEndpoint()).isEqualTo(new SelectedEndpoint("none", "none", null));
        assertThat(raw.urlCandidates()).hasSize(1);
    }

    @Test
    void shouldReturnTheBatchWhenThereAreNoHandlers() {
        Container raw = container("running", Set.of(), Map.of());
        assertThat(new ContainerEnrichment(List.of())
                        .enrich(List.of(raw), new EnrichmentContext("local", "localhost", false, false)))
                .containsExactly(raw);
    }

    @Test
    void shouldContinueAfterAHandlerThatMakesNoChanges() {
        ContainerEnricher noChange = (containers, context) -> containers;
        ContainerEnricher next = (containers, context) -> {
            containers.forEach(container -> container.setUrlCandidates(
                    List.of(new Endpoint("next", "next", "https://" + context.publishedHost() + "/", false))));
            return containers;
        };
        Container enriched = new ContainerEnrichment(List.of(noChange, next))
                .enrich(
                        List.of(container("running", Set.of(), Map.of())),
                        new EnrichmentContext("local", "app.example.com", false, false))
                .getFirst();
        assertThat(enriched.urlCandidates())
                .containsExactly(new Endpoint("next", "next", "https://app.example.com/", false));
    }

    @Test
    void shouldStopTheChainWhenAHandlerRemovesEveryContainer() {
        ContainerEnricher reject = (containers, context) -> List.of();
        ContainerEnricher later = (containers, context) -> {
            throw new AssertionError("Rejected containers must not reach later handlers");
        };
        assertThat(new ContainerEnrichment(List.of(reject, later))
                        .enrich(
                                List.of(container("running", Set.of(), Map.of())),
                                new EnrichmentContext("local", "localhost", false, false)))
                .isEmpty();
    }

    private Container container(String state, Set<Integer> ports, Map<String, String> labels) {
        return new Container(
                "id",
                "local",
                "app",
                "app",
                "app:latest",
                new Container.Details(ports, labels, null, state, "Up"),
                List.of());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ContainerStateFilter.class, UrlCandidateEnricher.class, EndpointAvailabilityFilter.class})
    static class ChainConfiguration {
        @Bean
        ContainerEnrichment containerEnrichment(
                ContainerStateFilter state,
                UrlCandidateEnricher urls,
                EndpointAvailabilityFilter availability,
                ContainerEnricher laterEnricher) {
            return new ContainerEnrichment(List.of(state, urls, availability, laterEnricher));
        }

        @Bean
        ContainerEnricher laterEnricher() {
            return (containers, context) -> {
                for (Container container : containers) {
                    if (container.urlCandidates().isEmpty()) continue;
                    List<Endpoint> urls = new ArrayList<>(container.urlCandidates());
                    urls.add(new Endpoint("later", "later", urls.getFirst().url() + "later", false));
                    container.setUrlCandidates(urls);
                }
                return containers;
            };
        }

        @Bean
        EndpointDiscovery endpointDiscovery() {
            return new EndpointDiscovery(List.of(
                    new PublishedPortEndpointProvider(new PublishedHostResolver()),
                    new TraefikEndpointProvider(new TraefikRuleParser())));
        }
    }
}
