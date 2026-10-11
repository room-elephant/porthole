package com.roomelephant.porthole.domain.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.config.properties.EndpointProperties;
import com.roomelephant.porthole.domain.component.endpoint.*;
import com.roomelephant.porthole.domain.component.enrichment.*;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.ContainerContext;
import com.roomelephant.porthole.domain.model.Endpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;

class ContainerEnrichmentTest {
    @Test
    void shouldDiscoverAndOrderHandlersAndPassEnrichedOutputToTheNextHandler() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            Container raw = container("running", Set.of(8080), Map.of("custom.metadata", "retained"));
            Container enriched = context.getBean(ContainerEnrichment.class)
                    .enrich(raw, new ContainerContext("192.168.50.6", false, false))
                    .orElseThrow();
            assertThat(enriched.urlCandidates())
                    .containsExactly(
                            new Endpoint("published-port:8080", "published-port", "http://192.168.50.6:8080/"),
                            new Endpoint("later", "later", "http://192.168.50.6:8080/later"));
            assertThat(enriched.labels()).containsEntry("custom.metadata", "retained");
            assertThat(enriched.publishedPorts()).containsExactly(8080);
            assertThat(raw.urlCandidates()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"created", "exited", "dead", "removing"})
    void shouldRejectStoppedContainersBeforeUrlDiscoveryOrLaterHandlers(String state) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            Container raw = container(state, Set.of(8080), Map.of());
            assertThat(context.getBean(ContainerEnrichment.class)
                            .enrich(raw, new ContainerContext("localhost", true, false)))
                    .isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"running", "paused", "restarting"})
    @NullSource
    void shouldKeepActiveOrUnknownStateContainers(String state) {
        ContainerStateFilter filter = new ContainerStateFilter();
        assertThat(filter.enrich(
                        container(state, Set.of(8080), Map.of()), new ContainerContext("localhost", false, false)))
                .isPresent();
    }

    @Test
    void shouldIncludeStoppedContainersWhenRequested() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            Container raw = container("exited", Set.of(8080), Map.of());
            assertThat(context.getBean(ContainerEnrichment.class)
                            .enrich(raw, new ContainerContext("localhost", false, true)))
                    .isPresent();
        }
    }

    @Test
    void shouldKeepLabelOnlyContainersWhenEndpointsAreRequired() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            Container raw =
                    container("running", Set.of(), Map.of("traefik.http.routers.app.rule", "Host(`app.example.com`)"));
            Container enriched = context.getBean(ContainerEnrichment.class)
                    .enrich(raw, new ContainerContext("localhost", false, false))
                    .orElseThrow();
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
                                    container("running", Set.of(), Map.of()),
                                    new ContainerContext("localhost", false, false)))
                    .isEmpty();
        }
    }

    @Test
    void shouldIncludeContainersWithoutUrlsWhenRequested() {
        ContainerEnrichment chain = new ContainerEnrichment(List.of(new EndpointAvailabilityFilter()));
        assertThat(chain.enrich(
                        container("running", Set.of(), Map.of()), new ContainerContext("localhost", true, false)))
                .isPresent();
    }

    @Test
    void shouldReturnTheContainerWhenThereAreNoHandlers() {
        Container raw = container("running", Set.of(), Map.of());
        assertThat(new ContainerEnrichment(List.of()).enrich(raw, new ContainerContext("localhost", false, false)))
                .containsSame(raw);
    }

    @Test
    void shouldContinueAfterAHandlerThatMakesNoChanges() {
        ContainerEnricher noChange = (container, context) -> Optional.of(container);
        ContainerEnricher next = (container, context) -> Optional.of(
                container.withUrlCandidates(List.of(new Endpoint("next", "next", "https://" + context.host() + "/"))));
        Container enriched = new ContainerEnrichment(List.of(noChange, next))
                .enrich(container("running", Set.of(), Map.of()), new ContainerContext("app.example.com", false, false))
                .orElseThrow();
        assertThat(enriched.urlCandidates()).containsExactly(new Endpoint("next", "next", "https://app.example.com/"));
    }

    @Test
    void shouldStopTheChainWhenAnyHandlerRejectsAContainer() {
        ContainerEnricher reject = (container, context) -> Optional.empty();
        ContainerEnricher later = (container, context) -> {
            throw new AssertionError("Rejected containers must not reach later handlers");
        };
        assertThat(new ContainerEnrichment(List.of(reject, later))
                        .enrich(
                                container("running", Set.of(), Map.of()),
                                new ContainerContext("localhost", false, false)))
                .isEmpty();
    }

    private Container container(String state, Set<Integer> ports, Map<String, String> labels) {
        return new Container("id", "app", "app", "app:latest", ports, labels, List.of(), "icon", null, state, "Up");
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
        ContainerEnrichment.class,
        ContainerStateFilter.class,
        UrlCandidateEnricher.class,
        EndpointAvailabilityFilter.class
    })
    static class ChainConfiguration {
        @Bean
        @Order(300)
        ContainerEnricher laterEnricher() {
            return (container, context) -> {
                if (container.urlCandidates().isEmpty()) return Optional.of(container);
                List<Endpoint> urls = new ArrayList<>(container.urlCandidates());
                urls.add(new Endpoint("later", "later", urls.getFirst().url() + "later"));
                return Optional.of(container.withUrlCandidates(urls));
            };
        }

        @Bean
        EndpointDiscovery endpointDiscovery() {
            return new EndpointDiscovery(List.of(
                    new PublishedPortEndpointProvider(new PublishedHostResolver(new EndpointProperties(null))),
                    new TraefikEndpointProvider(new TraefikRuleParser())));
        }
    }
}
