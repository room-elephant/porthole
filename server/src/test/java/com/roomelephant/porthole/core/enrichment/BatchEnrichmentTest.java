package com.roomelephant.porthole.core.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.model.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BatchEnrichmentTest {
    private final EnrichmentContext context = new EnrichmentContext("local", null, false, false);

    @Test
    void finishesEachStageAcrossTheBatchBeforeTheNextStage() {
        var first = container("first");
        var second = container("second");
        List<String> visits = new ArrayList<>();
        ContainerEnricher names = (containers, ctx) -> {
            containers.forEach(c -> {
                visits.add("name:" + c.id());
                c.setDisplayName("named-" + c.name());
            });
            return containers;
        };
        ContainerEnricher icons = (containers, ctx) -> {
            assertThat(containers).extracting(Container::displayName).containsExactly("named-first", "named-second");
            containers.forEach(c -> {
                visits.add("icon:" + c.id());
                c.setIconUrl(c.displayName() + ".svg");
            });
            return containers;
        };

        var result = new ContainerEnrichment(List.of(names, icons)).enrich(List.of(first, second), context);

        assertThat(result).containsExactly(first, second);
        assertThat(result).extracting(Container::iconUrl).containsExactly("named-first.svg", "named-second.svg");
        assertThat(visits).containsExactly("name:first", "name:second", "icon:first", "icon:second");
    }

    @Test
    void laterStagesReceiveOnlyTheRetainedContainers() {
        var hidden = container("hidden");
        var visible = container("visible");
        ContainerEnricher filter = (containers, ctx) ->
                containers.stream().filter(c -> c.id().equals("visible")).toList();
        ContainerEnricher icon = (containers, ctx) -> {
            containers.forEach(c -> c.setIconUrl("icon.svg"));
            return containers;
        };
        var result = new ContainerEnrichment(List.of(filter, icon)).enrich(List.of(hidden, visible), context);
        assertThat(result).containsExactly(visible);
        assertThat(visible.iconUrl()).isEqualTo("icon.svg");
        assertThat(hidden.iconUrl()).isEmpty();
    }

    @Test
    void skipsStagesForAnEmptyInventory() {
        ContainerEnricher unexpected = (containers, ctx) -> {
            throw new AssertionError("No containers to enrich");
        };
        assertThat(new ContainerEnrichment(List.of(unexpected)).enrich(List.of(), context))
                .isEmpty();
    }

    private Container container(String name) {
        return new Container(
                name,
                "local",
                name,
                name,
                "nginx",
                new Container.Details(Set.of(), Map.of(), null, "running", "Up"),
                List.of());
    }
}
