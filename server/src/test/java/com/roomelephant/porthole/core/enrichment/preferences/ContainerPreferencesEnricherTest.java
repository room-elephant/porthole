package com.roomelephant.porthole.core.enrichment.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.enrichment.icon.IconSettings;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.Endpoint;
import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryService;
import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.entries.Icon;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContainerPreferencesEnricherTest {
    private static final CustomIconResolver ICONS =
            new CustomIconResolver(new IconSettings("https://cdn.example.com/icons", ".svg"));
    private static final ContainerRef APP = new ContainerRef("local", "app");

    private final EnrichmentContext context = new EnrichmentContext("local", "localhost", false, false);

    private static EntryRecord endpoint(ContainerRef ref, EndpointChoice choice) {
        return new EntryRecord(ref, null, null, choice, null);
    }

    private static EntryService entries(EntryRecord... records) {
        EntryService service = mock(EntryService.class);
        when(service.state()).thenReturn(new EntryState(List.of(records)));
        return service;
    }

    @Test
    void missingEntryLeavesDefaults() {
        var container = container("id", "local", "app", List.of());
        assertThat(container.entry()).isNull();
        container.setEntry(endpoint(APP, EndpointChoice.NONE));

        var batch = List.of(container);
        assertThat(new ContainerPreferencesEnricher(entries(), ICONS).enrich(batch, context))
                .isSameAs(batch);

        assertThat(container.entry()).isNull();
    }

    @Test
    void shouldMatchRawSourceAndNameAcrossRecreationWithoutUsingDisplayNames() {
        var custom = endpoint(APP, new EndpointChoice.Custom("https://app.example.com"));
        var none = endpoint(new ContainerRef("remote", "app"), EndpointChoice.NONE);
        var enricher = new ContainerPreferencesEnricher(entries(custom, none), ICONS);
        var original = container("old-id", "local", "app", List.of());
        original.setDisplayName("Pretty name");
        var recreated = container("new-id", "local", "app", List.of());
        var remote = container("old-id", "remote", "app", List.of());
        var renamed = container("old-id", "local", "renamed", List.of());
        renamed.setDisplayName("app");

        enricher.enrich(List.of(original, remote, renamed), context);
        enricher.enrich(List.of(recreated), context);

        assertThat(original.entry()).isEqualTo(custom);
        assertThat(recreated.entry()).isEqualTo(custom);
        assertThat(remote.entry()).isEqualTo(none);
        assertThat(renamed.entry()).isNull();
    }

    @Test
    void appliesEntryIconAndEndpoint() {
        var saved = new EntryRecord(
                APP, null, new Icon.Library("coredns"), new EndpointChoice.Discovered("published-port:80"), null);
        var container = container(
                "id",
                "local",
                "app",
                List.of(
                        new Endpoint("traefik:app", "traefik", "https://app.example.com", false),
                        new Endpoint("published-port:80", "published-port", "http://localhost/", false)));

        new ContainerPreferencesEnricher(entries(saved), ICONS).enrich(List.of(container), context);

        assertThat(container.iconUrl()).isEqualTo("https://cdn.example.com/icons/coredns.svg");
        assertThat(container.urlCandidates())
                .containsExactly(
                        new Endpoint("traefik:app", "traefik", "https://app.example.com", false),
                        new Endpoint("published-port:80", "published-port", "http://localhost/", true));
    }

    @Test
    void shouldRetainUnavailableDiscoveredMetadataWhileSelectingFallback() {
        var saved = endpoint(APP, new EndpointChoice.Discovered("published-port:9000"));
        var container = container(
                "id",
                "local",
                "app",
                List.of(new Endpoint("traefik:app", "traefik", "https://app.example.com", false)));

        new ContainerPreferencesEnricher(entries(saved), ICONS).enrich(List.of(container), context);

        assertThat(container.entry()).isEqualTo(saved);
        assertThat(container.urlCandidates())
                .containsExactly(new Endpoint("traefik:app", "traefik", "https://app.example.com", true));
    }

    @Test
    void shouldUseOneSnapshotPerBatchAndReadCurrentEntriesOnTheNextBatch() {
        var saved = endpoint(APP, EndpointChoice.NONE);
        EntryService service = mock(EntryService.class);
        when(service.state()).thenReturn(new EntryState(List.of(saved)), EntryState.EMPTY);
        var enricher = new ContainerPreferencesEnricher(service, ICONS);
        var first = container("first", "local", "app", List.of());
        var second = container("second", "local", "app", List.of());

        enricher.enrich(List.of(first, second), context);

        assertThat(first.entry()).isEqualTo(saved);
        assertThat(second.entry()).isEqualTo(saved);
        enricher.enrich(List.of(first), context);
        assertThat(first.entry()).isNull();
        verify(service, org.mockito.Mockito.times(2)).state();
    }

    @Test
    void shouldAppendSelectedCustomCandidateAndUnselectDiscoveredCandidates() {
        var container = container(
                "id",
                "local",
                "app",
                List.of(new Endpoint("published-port:80", "published-port", "http://localhost/", true)));

        new ContainerPreferencesEnricher(
                        entries(endpoint(APP, new EndpointChoice.Custom("https://custom.example"))), ICONS)
                .enrich(List.of(container), context);

        assertThat(container.urlCandidates())
                .containsExactly(
                        new Endpoint("published-port:80", "published-port", "http://localhost/", false),
                        new Endpoint("custom", "custom", "https://custom.example", true));
    }

    @Test
    void shouldRetainCandidatesButClearSelectionForExplicitNone() {
        var container = container(
                "id",
                "local",
                "app",
                List.of(new Endpoint("published-port:80", "published-port", "http://localhost/", true)));

        new ContainerPreferencesEnricher(entries(endpoint(APP, EndpointChoice.NONE)), ICONS)
                .enrich(List.of(container), context);

        assertThat(container.urlCandidates())
                .containsExactly(new Endpoint("published-port:80", "published-port", "http://localhost/", false));
        assertThat(container.hasWebUi()).isFalse();
    }

    @Test
    void shouldSelectTheAutomaticCandidateWhenNothingIsSaved() {
        var container = container(
                "id",
                "local",
                "app",
                List.of(new Endpoint("published-port:80", "published-port", "http://localhost/", false)));

        new ContainerPreferencesEnricher(entries(), ICONS).enrich(List.of(container), context);

        assertThat(container.urlCandidates())
                .containsExactly(new Endpoint("published-port:80", "published-port", "http://localhost/", true));
    }

    private String iconUrlAfterEnriching(EntryRecord record) {
        var container = container("id", "local", "app", List.of());
        container.setIconUrl("https://cdn.example.com/icons/default.svg");
        new ContainerPreferencesEnricher(entries(record), ICONS)
                .enrich(List.of(container), new EnrichmentContext("local", null, true, false));
        return container.iconUrl();
    }

    @Test
    void savedUploadOverridesTheDefaultIconEvenIfItsFileHasVanished() {
        String id = "f".repeat(64) + ".png";
        assertThat(iconUrlAfterEnriching(new EntryRecord(APP, null, new Icon.Upload(id), null, null)))
                .isEqualTo("/api/icons/" + id);
    }

    @Test
    void noSavedIconKeepsTheDefaultIcon() {
        assertThat(iconUrlAfterEnriching(endpoint(APP, EndpointChoice.NONE)))
                .isEqualTo("https://cdn.example.com/icons/default.svg");
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
