package com.roomelephant.porthole.core.enrichment.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContainerPreferencesEnricherTest {
    private final EnrichmentContext context = new EnrichmentContext("local", "localhost", false, false);

    @Test
    void shouldDefaultToAutomaticBeforeEnrichmentAndWhenNoPreferenceIsStored() {
        var container = container("id", "local", "app", List.of());
        assertThat(container.preferences().selectedEndpoint()).isNull();
        container.setPreferences(new ContainerPreferences(new SelectedEndpoint("none", "none", null)));

        var batch = List.of(container);
        assertThat(new ContainerPreferencesEnricher(store(Map.of())).enrich(batch, context))
                .isSameAs(batch);

        assertThat(container.preferences().selectedEndpoint()).isNull();
    }

    @Test
    void shouldMatchRawSourceAndNameAcrossRecreationWithoutUsingDisplayNames() {
        var custom = new ContainerPreferences(new SelectedEndpoint("custom", "custom", "https://app.example.com"));
        var none = new ContainerPreferences(new SelectedEndpoint("none", "none", null));
        var enricher = new ContainerPreferencesEnricher(store(Map.of(
                new ContainerPreferencesKey("local", "app"), custom,
                new ContainerPreferencesKey("remote", "app"), none)));
        var original = container("old-id", "local", "app", List.of());
        original.setDisplayName("Pretty name");
        var recreated = container("new-id", "local", "app", List.of());
        var remote = container("old-id", "remote", "app", List.of());
        var renamed = container("old-id", "local", "renamed", List.of());
        renamed.setDisplayName("app");

        enricher.enrich(List.of(original, remote, renamed), context);
        enricher.enrich(List.of(recreated), context);

        assertThat(original.preferences()).isEqualTo(custom);
        assertThat(recreated.preferences()).isEqualTo(custom);
        assertThat(remote.preferences()).isEqualTo(none);
        assertThat(renamed.preferences().selectedEndpoint()).isNull();
    }

    @Test
    void shouldRetainUnavailableDiscoveredSelectionWithoutChangingCandidates() {
        var saved = new ContainerPreferences(new SelectedEndpoint("published-port:9000", "published-port", null));
        var container = container(
                "id", "local", "app", List.of(new Endpoint("traefik:app", "traefik", "https://app.example.com")));
        var candidates = container.urlCandidates();

        new ContainerPreferencesEnricher(store(Map.of(new ContainerPreferencesKey("local", "app"), saved)))
                .enrich(List.of(container), context);

        assertThat(container.preferences()).isEqualTo(saved);
        assertThat(container.urlCandidates()).isSameAs(candidates);
    }

    @Test
    void shouldUseOneSnapshotPerBatchAndReadCurrentPreferencesOnTheNextBatch() {
        var saved = new ContainerPreferences(new SelectedEndpoint("none", "none", null));
        var store = new ContainerPreferencesStore() {
            private boolean first = true;

            @Override
            public Map<ContainerPreferencesKey, ContainerPreferences> snapshot() {
                if (!first) return Map.of();
                first = false;
                return Map.of(new ContainerPreferencesKey("local", "app"), saved);
            }

            @Override
            public ContainerPreferences replaceSelection(ContainerPreferencesKey key, SelectedEndpoint selection) {
                throw new AssertionError("Inventory must not persist preferences");
            }
        };
        var enricher = new ContainerPreferencesEnricher(store);
        var first = container("first", "local", "app", List.of());
        var second = container("second", "local", "app", List.of());

        enricher.enrich(List.of(first, second), context);

        assertThat(first.preferences()).isEqualTo(saved);
        assertThat(second.preferences()).isEqualTo(saved);
        enricher.enrich(List.of(first), context);
        assertThat(first.preferences().selectedEndpoint()).isNull();
    }

    private ContainerPreferencesStore store(Map<ContainerPreferencesKey, ContainerPreferences> snapshot) {
        return new ContainerPreferencesStore() {
            @Override
            public Map<ContainerPreferencesKey, ContainerPreferences> snapshot() {
                return snapshot;
            }

            @Override
            public ContainerPreferences replaceSelection(ContainerPreferencesKey key, SelectedEndpoint selection) {
                throw new AssertionError("Inventory must not persist preferences");
            }
        };
    }

    private Container container(String id, String sourceId, String name, List<Endpoint> endpoints) {
        return new Container(
                id,
                sourceId,
                name,
                name,
                "app:latest",
                new Container.Details(Set.of(), Map.of(), null, "running", "Up"),
                endpoints);
    }
}
