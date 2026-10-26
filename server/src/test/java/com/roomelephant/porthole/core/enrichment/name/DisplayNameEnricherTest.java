package com.roomelephant.porthole.core.enrichment.name;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.ContainerEnrichment;
import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.Container;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DisplayNameEnricherTest {
    private final DisplayNameEnricher enricher = new DisplayNameEnricher();

    @Test
    void resolvesNamesFromTheWholeBatchAndKeepsOriginalNames() {
        List<Container> containers = List.of(
                container("project-web-1", "project"),
                container("project-web-2", "project"),
                container("project-web-1-1", "project"),
                container("project-api-1", "project"),
                container("other-api-1", "other"),
                container("standalone-1", null),
                container("custom-1", "project"),
                container("project-worker", "project"));

        var context = new EnrichmentContext("local", null, false, false);
        var pipeline = new ContainerEnrichment(List.of(enricher));
        assertThat(pipeline.enrich(containers, context)).containsExactlyElementsOf(containers);

        assertThat(containers)
                .extracting(Container::displayName)
                .containsExactly("web-1", "web-2", "web-1-1", "api", "api", "standalone-1", "custom-1", "worker");
        assertThat(containers.getFirst().name()).isEqualTo("project-web-1");
    }

    @Test
    void keepsReplicaSuffixWhenItCollidesWithAnUnsuffixedName() {
        List<Container> containers =
                List.of(container("project-web", "project"), container("project-web-1", "project"));
        var context = new EnrichmentContext("local", null, false, false);
        var pipeline = new ContainerEnrichment(List.of(enricher));
        assertThat(pipeline.enrich(containers, context)).containsExactlyElementsOf(containers);
        assertThat(containers).extracting(Container::displayName).containsExactly("web", "web-1");
    }

    @Test
    void acceptsEmptyInventory() {
        List<Container> containers = List.of();
        var context = new EnrichmentContext("local", null, false, false);
        var pipeline = new ContainerEnrichment(List.of(enricher));
        assertThat(pipeline.enrich(containers, context)).containsExactlyElementsOf(containers);
        assertThat(containers).isEmpty();
    }

    @Test
    void consecutiveBatchesDoNotShareCollisionState() {
        var context = new EnrichmentContext("local", null, false, false);
        var first = List.of(container("project-web-1", "project"), container("project-web-2", "project"));
        var second = List.of(container("project-web-1", "project"));
        enricher.enrich(first, context);
        enricher.enrich(second, context);
        assertThat(first).extracting(Container::displayName).containsExactly("web-1", "web-2");
        assertThat(second).extracting(Container::displayName).containsExactly("web");
    }

    private Container container(String name, @Nullable String project) {
        return new Container(
                name,
                "local",
                name,
                name,
                "nginx",
                new Container.Details(Set.of(), Map.of(), project, null, null),
                List.of());
    }
}
