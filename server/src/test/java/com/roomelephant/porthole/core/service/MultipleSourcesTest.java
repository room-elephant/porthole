package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.core.enrichment.*;
import com.roomelephant.porthole.core.enrichment.name.DisplayNameEnricher;
import com.roomelephant.porthole.core.mapping.ContainerMapper;
import com.roomelephant.porthole.core.model.*;
import com.roomelephant.porthole.fleet.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MultipleSourcesTest {
    private static final Instant T = Instant.parse("2026-11-08T10:00:00Z");
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Source WALKER = new Source("walker", "Walker", "192.168.50.6", true, Duration.ofSeconds(1));
    private static final Source MIKE = new Source("mike", "Mike", "192.168.50.5", false, Duration.ofSeconds(1));

    private final Fleet fleet = mock(Fleet.class);

    private static ContainerSnapshot snapshot(String name, Map<String, String> labels) {
        return new ContainerSnapshot("same-id", name, "nginx:1", "sha256:a", Set.of(), labels, "running", "Up");
    }

    private static SourceSnapshot up(Source source, ContainerSnapshot... containers) {
        return SourceSnapshot.initial(source).succeeded(List.of(containers), T);
    }

    private ContainerService service(ContainerEnrichment enrichment, SourceSnapshot... snapshots) {
        when(fleet.snapshots()).thenReturn(List.of(snapshots));
        return new ContainerService(fleet, new ContainerMapper(), enrichment, SelfContainer.none());
    }

    @Test
    void oneRequestCombinesEverySourceAndKeepsMatchingIdsSeparate() {
        var service = service(
                new ContainerEnrichment(List.of()),
                up(WALKER, snapshot("same-name", Map.of())),
                up(MIKE, snapshot("same-name", Map.of())));
        var result = service.getContainers(new ContainerQuery(true, true));
        assertThat(result.containers()).extracting(Container::sourceId).containsExactly("walker", "mike");
        assertThat(result.containers()).extracting(Container::id).containsExactly("same-id", "same-id");
        assertThat(result.sources())
                .containsExactly(
                        new SourceState("walker", "Walker", SourceStatus.UP, null, T),
                        new SourceState("mike", "Mike", SourceStatus.UP, null, T));
    }

    @Test
    void resolvesNameCollisionsSeparatelyForEachSource() {
        var labels = Map.of("com.docker.compose.project", "project");
        var service = service(
                new ContainerEnrichment(List.of(new DisplayNameEnricher())),
                up(WALKER, snapshot("project-web-1", labels)),
                up(MIKE, snapshot("project-web-1", labels)));
        assertThat(service.getContainers(new ContainerQuery(true, true)).containers())
                .extracting(Container::displayName)
                .containsExactly("web", "web");
    }

    @Test
    void sourceBeforeFirstPollIsDownWithoutContainers() {
        var service = service(new ContainerEnrichment(List.of()), SourceSnapshot.initial(WALKER), up(MIKE));
        var result = service.getContainers(new ContainerQuery(true, true));
        assertThat(result.containers()).isEmpty();
        assertThat(result.sources().getFirst())
                .isEqualTo(new SourceState("walker", "Walker", SourceStatus.DOWN, "Waiting for first check", null));
        verify(fleet).snapshots();
        verifyNoMoreInteractions(fleet);
    }

    @Test
    void downSourceStillListsRetainedContainers() {
        var down = up(WALKER, snapshot("app", Map.of()))
                .failed(SourceSnapshot.Status.DOWN, "Docker is not reachable", T.plusSeconds(60), RETENTION);
        var service = service(new ContainerEnrichment(List.of()), down, up(MIKE));
        var result = service.getContainers(new ContainerQuery(true, true));
        assertThat(result.containers()).extracting(Container::sourceId).containsExactly("walker");
        assertThat(result.sources().getFirst())
                .isEqualTo(new SourceState("walker", "Walker", SourceStatus.DOWN, "Docker is not reachable", T));
    }

    @Test
    void failedSourceWithoutRetainedContainersDoesNotDiscardHealthyResults() {
        var error = SourceSnapshot.initial(MIKE)
                .failed(SourceSnapshot.Status.ERROR, "Could not retrieve containers", T, RETENTION);
        var service = service(new ContainerEnrichment(List.of()), error, up(WALKER, snapshot("app", Map.of())));
        var result = service.getContainers(new ContainerQuery(true, true));
        assertThat(result.containers()).extracting(Container::sourceId).containsExactly("walker");
        assertThat(result.sources())
                .extracting(SourceState::status)
                .containsExactly(SourceStatus.ERROR, SourceStatus.UP);
    }

    @Test
    void enrichmentFailureKeepsLastSuccessAtAndOtherSources() {
        ContainerEnricher failingEnricher = (containers, context) -> {
            if (containers.getFirst().sourceId().equals("walker")) throw new IllegalStateException("private details");
            return containers;
        };
        var service = service(
                new ContainerEnrichment(List.of(failingEnricher)),
                up(WALKER, snapshot("app", Map.of())),
                up(MIKE, snapshot("app", Map.of())));
        var result = service.getContainers(new ContainerQuery(true, true));
        assertThat(result.sources().getFirst())
                .isEqualTo(new SourceState("walker", "Walker", SourceStatus.ERROR, "Could not retrieve containers", T));
        assertThat(result.containers()).extracting(Container::sourceId).containsExactly("mike");
    }
}
