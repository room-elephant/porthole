package com.roomelephant.porthole.domain.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.domain.component.endpoint.*;
import com.roomelephant.porthole.domain.component.enrichment.ContainerEnricher;
import com.roomelephant.porthole.domain.component.enrichment.UrlCandidateEnricher;
import com.roomelephant.porthole.domain.model.Container;
import com.roomelephant.porthole.domain.model.Endpoint;
import com.roomelephant.porthole.domain.model.EndpointContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;

class ContainerEnrichmentTest {
    @Test
    void shouldDiscoverAndOrderHandlersAndPassEnrichedOutputToTheNextHandler() {
        try (var context = new AnnotationConfigApplicationContext(ChainConfiguration.class)) {
            var raw = container();
            var enriched = context.getBean(ContainerEnrichment.class).enrich(raw, new EndpointContext("192.168.50.6"));

            assertThat(enriched.urlCandidates())
                    .containsExactly(
                            new Endpoint("published-port:8080", "published-port", "http://192.168.50.6:8080/"),
                            new Endpoint("later", "later", "http://192.168.50.6:8080/later"));
            assertThat(enriched.labels()).containsEntry("custom.metadata", "retained");
            assertThat(enriched.publishedPorts()).containsExactly(8080);
            assertThat(raw.urlCandidates()).isEmpty();
        }
    }

    @Test
    void shouldReturnTheContainerWhenThereAreNoHandlers() {
        var raw = container();
        assertThat(new ContainerEnrichment(List.of()).enrich(raw, new EndpointContext("localhost")))
                .isSameAs(raw);
    }

    @Test
    void shouldContinueAfterAHandlerThatMakesNoChanges() {
        ContainerEnricher noChange = (container, context) -> container;
        ContainerEnricher next = (container, context) -> container.withUrlCandidates(
                List.of(new Endpoint("next", "next", "https://" + context.publishedHost() + "/")));
        var enriched = new ContainerEnrichment(List.of(noChange, next))
                .enrich(container(), new EndpointContext("app.example.com"));
        assertThat(enriched.urlCandidates()).containsExactly(new Endpoint("next", "next", "https://app.example.com/"));
    }

    private Container container() {
        return new Container(
                "id",
                "app",
                "app",
                "app:latest",
                Set.of(8080),
                Map.of("custom.metadata", "retained"),
                List.of(),
                "icon",
                null,
                "running",
                "Up");
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ContainerEnrichment.class, UrlCandidateEnricher.class})
    static class ChainConfiguration {
        @Bean
        @Order(100)
        ContainerEnricher laterEnricher() {
            return (container, context) -> {
                var urls = new ArrayList<>(container.urlCandidates());
                urls.add(new Endpoint("later", "later", urls.getFirst().url() + "later"));
                return container.withUrlCandidates(urls);
            };
        }

        @Bean
        EndpointDiscovery endpointDiscovery() {
            return new EndpointDiscovery(List.of(new PublishedPortEndpointProvider()));
        }
    }
}
