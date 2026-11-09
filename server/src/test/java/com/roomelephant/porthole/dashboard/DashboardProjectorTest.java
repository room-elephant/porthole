package com.roomelephant.porthole.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.endpoints.EndpointDiscovery;
import com.roomelephant.porthole.endpoints.PublishedHostResolver;
import com.roomelephant.porthole.endpoints.PublishedPortEndpointProvider;
import com.roomelephant.porthole.endpoints.TraefikEndpointProvider;
import com.roomelephant.porthole.endpoints.TraefikRuleParser;
import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.entries.Icon;
import com.roomelephant.porthole.fleet.ContainerKey;
import com.roomelephant.porthole.fleet.ContainerSnapshot;
import com.roomelephant.porthole.fleet.SelfContainer;
import com.roomelephant.porthole.fleet.Source;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import com.roomelephant.porthole.versions.VersionChecks;
import com.roomelephant.porthole.versions.VersionInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DashboardProjectorTest {
    private static final VersionChecks NO_CHECKS = new VersionChecks(false, null);
    private static final Instant T = Instant.parse("2026-11-08T10:00:00Z");
    private static final Source WALKER = new Source("walker", "Walker", "walker.lan", true, Duration.ofSeconds(1));
    private static final Source MIKE = new Source("mike", "Mike", null, false, Duration.ofSeconds(1));
    private static final LinkRef ROUTER = new LinkRef("3f2a9c1e5b7d4e8a9c217d4e5f6a8b90");
    private static final LinkRef NAS = new LinkRef("0".repeat(32));
    private static final String UPLOAD = "a".repeat(64) + ".png";
    private static final VersionLookup NO_VERSIONS = key -> null;

    private final EndpointDiscovery discovery = new EndpointDiscovery(List.of(
            new PublishedPortEndpointProvider(new PublishedHostResolver()),
            new TraefikEndpointProvider(new TraefikRuleParser())));
    private final DashboardProjector projector =
            new DashboardProjector(discovery, new DefaultIcons(Map.of()), new SelfContainer("self-id"));

    private static ContainerSnapshot container(
            String name, @Nullable String project, Set<Integer> ports, Map<String, String> extraLabels) {
        Map<String, String> labels = new HashMap<>(extraLabels);
        if (project != null) labels.put("com.docker.compose.project", project);
        return new ContainerSnapshot(name + "-id", name, "nginx:1.0", "img", ports, labels, "running", "Up 2 hours");
    }

    private static ContainerSnapshot container(String name, Set<Integer> ports) {
        return container(name, null, ports, Map.of());
    }

    private static ContainerSnapshot stopped(String name) {
        return new ContainerSnapshot(name + "-id", name, "nginx:1.0", "img", Set.of(), Map.of(), "exited", "Exited");
    }

    private static SourceSnapshot up(Source source, ContainerSnapshot... containers) {
        return SourceSnapshot.initial(source).succeeded(List.of(containers), T);
    }

    private static Map<String, String> traefik(String... hosts) {
        Map<String, String> labels = new HashMap<>();
        for (int index = 0; index < hosts.length; index++) {
            String[] parts = hosts[index].split("/", 2);
            String rule = parts.length == 1
                    ? "Host(`" + parts[0] + "`)"
                    : "Host(`" + parts[0] + "`) && PathPrefix(`/" + parts[1] + "`)";
            labels.put("traefik.http.routers.r" + index + ".rule", rule);
        }
        return labels;
    }

    private static EntryRecord saved(String source, String name, EndpointChoice choice) {
        return new EntryRecord(new ContainerRef(source, name), null, null, choice, null);
    }

    private Dashboard project(List<SourceSnapshot> sources, EntryState entries) {
        return projector.project(7, NO_CHECKS, sources, entries, NO_VERSIONS);
    }

    private DashboardEntry only(Dashboard dashboard) {
        assertThat(dashboard.entries()).hasSize(1);
        return dashboard.entries().getFirst();
    }

    @Test
    void projectsContainersBySourceThenLinksInSavedOrder() {
        EntryState entries = EntryState.EMPTY
                .with(new EntryRecord(NAS, "NAS", null, new EndpointChoice.Custom("http://nas/"), null))
                .with(new EntryRecord(ROUTER, "Router", null, new EndpointChoice.Custom("http://r/"), "base"));

        Dashboard dashboard = project(
                List.of(
                        up(WALKER, container("b", Set.of()), container("a", Set.of())),
                        up(MIKE, container("c", Set.of()))),
                entries);

        assertThat(dashboard.sources())
                .extracting(snapshot -> snapshot.source().id())
                .containsExactly("walker", "mike");
        assertThat(dashboard.entries())
                .extracting(entry -> entry.ref().id())
                .containsExactly("container:walker:b", "container:walker:a", "container:mike:c", NAS.id(), ROUTER.id());
    }

    @Test
    void includesStoppedAndEndpointlessContainers() {
        Dashboard dashboard =
                project(List.of(up(WALKER, stopped("old"), container("quiet", Set.of()))), EntryState.EMPTY);

        assertThat(dashboard.entries()).hasSize(2);
        assertThat(dashboard.entries().getFirst().container().state()).isEqualTo("exited");
        assertThat(dashboard.entries().get(1).endpoints()).isEmpty();
        assertThat(dashboard.entries().get(1).hasWebUi()).isFalse();
    }

    @Test
    void nameOverrideReplacesDisplayNameAndKeepsDefaultName() {
        EntryState entries = EntryState.EMPTY.with(
                new EntryRecord(new ContainerRef("walker", "smh-sonarr-1"), "TV", null, null, null));

        DashboardEntry entry =
                only(project(List.of(up(WALKER, container("smh-sonarr-1", "smh", Set.of(), Map.of()))), entries));

        assertThat(entry.name()).isEqualTo("TV");
        assertThat(entry.container().defaultName()).isEqualTo("sonarr");
        assertThat(entry.container().name()).isEqualTo("smh-sonarr-1");
    }

    @Test
    void nameOverrideDoesNotAffectCollisions() {
        EntryState entries =
                EntryState.EMPTY.with(new EntryRecord(new ContainerRef("walker", "p-web-1"), "api", null, null, null));

        Dashboard dashboard = project(
                List.of(up(
                        WALKER,
                        container("p-web-1", "p", Set.of(), Map.of()),
                        container("p-api-1", "p", Set.of(), Map.of()))),
                entries);

        assertThat(dashboard.entries()).extracting(DashboardEntry::name).containsExactly("api", "api");
        assertThat(dashboard.entries())
                .extracting(entry -> entry.container().defaultName())
                .containsExactly("web", "api");
    }

    @Test
    void stackIsComposeProject() {
        DashboardEntry entry = only(project(
                List.of(up(WALKER, container("smh-sonarr-1", "smh", Set.of(), Map.of()))), EntryState.EMPTY));

        assertThat(entry.stack()).isEqualTo("smh");
        assertThat(only(project(List.of(up(WALKER, container("solo", Set.of()))), EntryState.EMPTY))
                        .stack())
                .isNull();
    }

    @Test
    void automaticSelectionPrefersRoutedCandidates() {
        DashboardEntry entry = only(project(
                List.of(up(WALKER, container("app", null, Set.of(8080), traefik("app.example.com/admin")))),
                EntryState.EMPTY));

        assertThat(entry.endpoints())
                .containsExactly(
                        new ResolvedEndpoint("published-port:8080", "published-port", "http://walker.lan:8080/", false),
                        new ResolvedEndpoint(
                                "traefik:http://app.example.com/admin",
                                "traefik",
                                "http://app.example.com/admin",
                                true));
        assertThat(entry.hasWebUi()).isTrue();
    }

    @Test
    void savedDiscoveredChoiceSelectsItsCandidate() {
        EntryState entries =
                EntryState.EMPTY.with(saved("walker", "app", new EndpointChoice.Discovered("published-port:8080")));

        DashboardEntry entry = only(project(
                List.of(up(WALKER, container("app", null, Set.of(8080), traefik("app.example.com")))), entries));

        assertThat(entry.endpoints()).extracting(ResolvedEndpoint::selected).containsExactly(true, false);
    }

    @Test
    void missingDiscoveredChoiceFallsBackToAutomatic() {
        EntryState entries =
                EntryState.EMPTY.with(saved("walker", "app", new EndpointChoice.Discovered("published-port:9999")));

        DashboardEntry entry = only(project(List.of(up(WALKER, container("app", Set.of(8080)))), entries));

        assertThat(entry.endpoints()).extracting(ResolvedEndpoint::selected).containsExactly(true);
    }

    @Test
    void customChoiceAppendsSelectedCustomEndpoint() {
        EntryState entries =
                EntryState.EMPTY.with(saved("walker", "app", new EndpointChoice.Custom("https://app.example/")));

        DashboardEntry entry = only(project(List.of(up(WALKER, container("app", Set.of(8080)))), entries));

        assertThat(entry.endpoints())
                .containsExactly(
                        new ResolvedEndpoint("published-port:8080", "published-port", "http://walker.lan:8080/", false),
                        new ResolvedEndpoint("custom", "custom", "https://app.example/", true));
        assertThat(entry.hasWebUi()).isTrue();
    }

    @Test
    void customChoiceWorksWithoutDiscovery() {
        EntryState entries =
                EntryState.EMPTY.with(saved("walker", "app", new EndpointChoice.Custom("https://app.example/")));

        DashboardEntry entry = only(project(List.of(up(WALKER, container("app", Set.of()))), entries));

        assertThat(entry.endpoints())
                .containsExactly(new ResolvedEndpoint("custom", "custom", "https://app.example/", true));
    }

    @Test
    void noneChoiceDeselectsAllAndHasNoWebUi() {
        EntryState entries = EntryState.EMPTY.with(saved("walker", "app", EndpointChoice.NONE));

        DashboardEntry entry = only(project(List.of(up(WALKER, container("app", Set.of(8080)))), entries));

        assertThat(entry.endpoints()).extracting(ResolvedEndpoint::selected).containsExactly(false);
        assertThat(entry.hasWebUi()).isFalse();
    }

    @Test
    void hasWebUiFalseWithoutEndpoints() {
        assertThat(only(project(List.of(up(WALKER, container("app", Set.of()))), EntryState.EMPTY))
                        .hasWebUi())
                .isFalse();
    }

    @Test
    void ambiguousAutomaticChoiceStillHasWebUi() {
        DashboardEntry entry =
                only(project(List.of(up(WALKER, container("app", Set.of(8080, 9090)))), EntryState.EMPTY));

        assertThat(entry.endpoints()).extracting(ResolvedEndpoint::selected).containsExactly(false, false);
        assertThat(entry.hasWebUi()).isTrue();
    }

    @Test
    void savedIconWinsOverDefault() {
        EntryState entries = EntryState.EMPTY.with(
                new EntryRecord(new ContainerRef("walker", "app"), null, new Icon.Upload(UPLOAD), null, null));

        DashboardEntry entry = only(project(List.of(up(WALKER, container("app", Set.of()))), entries));

        assertThat(entry.icon()).isEqualTo(new IconSource.Upload(UPLOAD));
        assertThat(entry.iconChoice()).isEqualTo(new Icon.Upload(UPLOAD));
    }

    @Test
    void defaultIconFromImage() {
        DashboardEntry entry = only(project(List.of(up(WALKER, container("app", Set.of()))), EntryState.EMPTY));

        assertThat(entry.icon()).isEqualTo(new IconSource.Library("nginx"));
        assertThat(entry.iconChoice()).isNull();
    }

    @Test
    void selfContainerWithoutSavedIconUsesSelf() {
        ContainerSnapshot self =
                new ContainerSnapshot("self-id", "porthole", "porthole:1", "img", Set.of(), Map.of(), "running", "Up");

        DashboardEntry entry = only(project(List.of(up(WALKER, self)), EntryState.EMPTY));

        assertThat(entry.icon()).isEqualTo(new IconSource.Self());
        assertThat(entry.container().self()).isTrue();
    }

    @Test
    void selfIsOnlyMatchedOnLocalSourcesForShortIds() {
        ContainerSnapshot remote =
                new ContainerSnapshot("self-id", "porthole", "porthole:1", "img", Set.of(), Map.of(), "running", "Up");

        assertThat(only(project(List.of(up(MIKE, remote)), EntryState.EMPTY))
                        .container()
                        .self())
                .isFalse();
    }

    @Test
    void carriesTheRevisionAndRunState() {
        VersionChecks running = new VersionChecks(true, Instant.EPOCH);

        Dashboard dashboard = projector.project(9, running, List.of(), EntryState.EMPTY, NO_VERSIONS);

        assertThat(dashboard.revision()).isEqualTo(9);
        assertThat(dashboard.versionChecks()).isEqualTo(running);
    }

    @Test
    void versionLookedUpBySourceAndContainerId() {
        VersionInfo version = new VersionInfo("1.0", "1.1", true);

        Dashboard dashboard = projector.project(
                7,
                NO_CHECKS,
                List.of(up(WALKER, container("app", Set.of()))),
                EntryState.EMPTY,
                key -> key.equals(new ContainerKey("walker", "app-id")) ? version : null);

        assertThat(only(dashboard).container().version()).isEqualTo(version);
    }

    @Test
    void containerFactsComeFromTheSnapshot() {
        ContainerFacts facts = only(project(List.of(up(WALKER, container("app", Set.of()))), EntryState.EMPTY))
                .container();

        assertThat(facts)
                .isEqualTo(
                        new ContainerFacts("walker", "app", "app", "nginx:1.0", "running", "Up 2 hours", false, null));
    }

    @Test
    void missingImageIsUnknown() {
        ContainerSnapshot imageless =
                new ContainerSnapshot("x-id", "x", null, null, Set.of(), Map.of(), "running", "Up");

        DashboardEntry entry = only(project(List.of(up(WALKER, imageless)), EntryState.EMPTY));

        assertThat(entry.container().image()).isEqualTo("Unknown");
        assertThat(entry.icon()).isEqualTo(new IconSource.Library("docker"));
    }

    @Test
    void containersWithoutIdsAreSkipped() {
        ContainerSnapshot idless = new ContainerSnapshot(null, "x", "nginx", null, Set.of(), Map.of(), "running", "Up");

        assertThat(project(List.of(up(WALKER, idless)), EntryState.EMPTY).entries())
                .isEmpty();
    }

    @Test
    void retainedContainersOfFailedSourceAreProjected() {
        EntryState entries =
                EntryState.EMPTY.with(new EntryRecord(new ContainerRef("walker", "app"), "Kept", null, null, null));
        SourceSnapshot failed = up(WALKER, container("app", Set.of(8080)))
                .failed(SourceSnapshot.Status.DOWN, "Docker is not reachable", T.plusSeconds(60), Duration.ofHours(24));

        Dashboard dashboard = project(List.of(failed), entries);

        assertThat(dashboard.sources().getFirst().status()).isEqualTo(SourceSnapshot.Status.DOWN);
        assertThat(only(dashboard).name()).isEqualTo("Kept");
        assertThat(only(dashboard).endpoints()).hasSize(1);
    }

    @Test
    void linkEntryHasCustomEndpointAndNoContainer() {
        EntryState entries = EntryState.EMPTY.with(new EntryRecord(
                ROUTER, "Router", new Icon.Library("router"), new EndpointChoice.Custom("http://r/"), "base"));

        DashboardEntry entry = only(project(List.of(), entries));

        assertThat(entry.ref()).isEqualTo(ROUTER);
        assertThat(entry.name()).isEqualTo("Router");
        assertThat(entry.stack()).isEqualTo("base");
        assertThat(entry.endpoints()).containsExactly(new ResolvedEndpoint("custom", "custom", "http://r/", true));
        assertThat(entry.hasWebUi()).isTrue();
        assertThat(entry.container()).isNull();
        assertThat(entry.icon()).isEqualTo(new IconSource.Library("router"));
    }

    @Test
    void linkWithoutIconHasNoneIconSource() {
        EntryState entries = EntryState.EMPTY.with(
                new EntryRecord(ROUTER, "Router", null, new EndpointChoice.Custom("http://r/"), null));

        assertThat(only(project(List.of(), entries)).icon()).isEqualTo(new IconSource.None());
    }

    @Test
    void savedRecordsForAbsentContainersAreNotProjected() {
        EntryState entries =
                EntryState.EMPTY.with(new EntryRecord(new ContainerRef("walker", "gone"), "Gone", null, null, null));

        assertThat(project(List.of(up(WALKER)), entries).entries()).isEmpty();
    }

    @Test
    void entryReturnsEmptyForUndiscoveredContainer() {
        assertThat(projector.entry(
                        new ContainerRef("walker", "gone"), List.of(up(WALKER)), EntryState.EMPTY, NO_VERSIONS))
                .isEmpty();
    }

    @Test
    void entryReturnsLinkAndContainer() {
        EntryState entries = EntryState.EMPTY.with(
                new EntryRecord(ROUTER, "Router", null, new EndpointChoice.Custom("http://r/"), null));
        List<SourceSnapshot> sources =
                List.of(up(WALKER, container("app", Set.of())), up(MIKE, container("app", Set.of())));

        assertThat(projector.entry(ROUTER, sources, entries, NO_VERSIONS))
                .map(DashboardEntry::name)
                .contains("Router");
        assertThat(projector.entry(new ContainerRef("mike", "app"), sources, entries, NO_VERSIONS))
                .map(entry -> entry.container().sourceId())
                .contains("mike");
        assertThat(projector.entry(new LinkRef("1".repeat(32)), sources, entries, NO_VERSIONS))
                .isEmpty();
    }
}
