package com.roomelephant.porthole.core.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.core.enrichment.endpoint.*;
import com.roomelephant.porthole.core.enrichment.endpointavailability.EndpointAvailabilityFilter;
import com.roomelephant.porthole.core.enrichment.icon.*;
import com.roomelephant.porthole.core.enrichment.name.DisplayNameEnricher;
import com.roomelephant.porthole.core.enrichment.state.ContainerStateFilter;
import com.roomelephant.porthole.core.enrichment.version.VersionEnricher;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.VersionInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PipelineWiringTest {
    @Test
    void productionStagesNameTheWholeInventoryAndEnrichOnlyRetainedContainers() {
        var versions = mock(VersionRefreshService.class);
        var version = new VersionInfo("1.0", "2.0", true);
        when(versions.getCachedVersionAndRefresh(any())).thenReturn(version);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(VersionRefreshService.class, () -> versions);
            context.registerBean(
                    EndpointDiscovery.class,
                    () -> new EndpointDiscovery(
                            List.of(new PublishedPortEndpointProvider(new PublishedHostResolver()))));
            context.registerBean(
                    IconResolver.class,
                    () -> new IconResolver(Map.of(), new IconSettings("https://icons.example.com", ".svg")));
            context.register(
                    ContainerEnrichment.class,
                    ContainerStateFilter.class,
                    UrlCandidateEnricher.class,
                    EndpointAvailabilityFilter.class,
                    IconEnricher.class,
                    DisplayNameEnricher.class,
                    VersionEnricher.class);
            context.refresh();
            var stopped = container("project-web-2", "exited", Set.of(8082));
            var web = container("project-web-1", "running", Set.of(8080));
            var internal = container("project-worker-1", "running", Set.of());
            var api = container("project-api-1", "running", Set.of(8081));

            var result = context.getBean(ContainerEnrichment.class)
                    .enrich(
                            List.of(stopped, web, internal, api),
                            new EnrichmentContext("local", "localhost", false, false));

            assertThat(result).containsExactly(web, api);
            assertThat(result).extracting(Container::displayName).containsExactly("web-1", "api");
            assertThat(result)
                    .extracting(Container::iconUrl)
                    .containsExactly("https://icons.example.com/nginx.svg", "https://icons.example.com/nginx.svg");
            assertThat(result).extracting(Container::version).containsExactly(version, version);
            assertThat(stopped.urlCandidates()).isEmpty();
            assertThat(stopped.iconUrl()).isEmpty();
            assertThat(internal.iconUrl()).isEmpty();
            assertThat(stopped.version()).isNull();
            assertThat(internal.version()).isNull();
        }
    }

    private Container container(String name, String state, Set<Integer> ports) {
        return new Container(
                name,
                "local",
                name,
                name,
                "nginx:1.0",
                new Container.Details(ports, Map.of(), "project", state, "Up"),
                List.of());
    }
}
