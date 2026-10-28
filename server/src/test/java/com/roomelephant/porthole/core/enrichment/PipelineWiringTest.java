package com.roomelephant.porthole.core.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.config.EnrichmentConfiguration;
import com.roomelephant.porthole.core.enrichment.endpoint.*;
import com.roomelephant.porthole.core.enrichment.icon.*;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.test.util.ReflectionTestUtils;

class PipelineWiringTest {
    @Test
    void shouldIncludeEveryEnricherExactlyOnce() {
        try (var context = context(mock(VersionRefreshService.class))) {
            assertThat(ReflectionTestUtils.getField(context.getBean(ContainerEnrichment.class), "enrichers"))
                    .asInstanceOf(InstanceOfAssertFactories.LIST)
                    .containsExactlyInAnyOrderElementsOf(
                            context.getBeansOfType(ContainerEnricher.class).values());
        }
    }

    @Test
    void productionStagesNameTheWholeInventoryAndEnrichOnlyRetainedContainers() {
        var versions = mock(VersionRefreshService.class);
        var version = new VersionInfo("1.0", "2.0", true);
        when(versions.getCachedVersionAndRefresh(any())).thenReturn(version);
        try (var context = context(versions)) {
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

    private AnnotationConfigApplicationContext context(VersionRefreshService versions) {
        return context(versions, Map.of());
    }

    @Test
    void shouldAttachPreferencesBeforeAvailabilityWithoutBypassingStateOrChangingDiscovery() {
        var custom = new ContainerPreferences(new SelectedEndpoint("custom", "custom", "https://worker.example.com"));
        var none = new ContainerPreferences(new SelectedEndpoint("none", "none", null));
        try (var context = context(
                mock(VersionRefreshService.class),
                Map.of(
                        new ContainerPreferencesKey("local", "project-worker-1"), custom,
                        new ContainerPreferencesKey("local", "project-stopped-1"), custom,
                        new ContainerPreferencesKey("local", "project-web-1"), none))) {
            var worker = container("project-worker-1", "running", Set.of());
            var stopped = container("project-stopped-1", "exited", Set.of());
            var web = container("project-web-1", "running", Set.of(8080));

            var result = context.getBean(ContainerEnrichment.class)
                    .enrich(List.of(worker, stopped, web), new EnrichmentContext("local", "localhost", false, false));

            assertThat(result).containsExactly(worker, web);
            assertThat(worker.preferences()).isEqualTo(custom);
            assertThat(worker.urlCandidates()).isEmpty();
            assertThat(worker.iconUrl()).isEqualTo("https://icons.example.com/nginx.svg");
            assertThat(web.preferences()).isEqualTo(none);
            assertThat(web.urlCandidates()).extracting(e -> e.url()).containsExactly("http://localhost:8080/");
            assertThat(stopped.preferences().selectedEndpoint()).isNull();
        }
    }

    private AnnotationConfigApplicationContext context(
            VersionRefreshService versions, Map<ContainerPreferencesKey, ContainerPreferences> preferences) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(VersionRefreshService.class, () -> versions);
        var store = mock(ContainerPreferencesStore.class);
        when(store.snapshot()).thenReturn(preferences);
        context.registerBean(ContainerPreferencesStore.class, () -> store);
        context.registerBean(
                EndpointDiscovery.class,
                () -> new EndpointDiscovery(List.of(new PublishedPortEndpointProvider(new PublishedHostResolver()))));
        context.registerBean(
                IconResolver.class,
                () -> new IconResolver(Map.of(), new IconSettings("https://icons.example.com", ".svg")));
        // Discover implementations independently so a new handler cannot be omitted from both lists.
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ContainerEnricher.class));
        for (var candidate : scanner.findCandidateComponents(ContainerEnricher.class.getPackageName())) {
            context.registerBeanDefinition(candidate.getBeanClassName(), candidate);
        }
        context.register(EnrichmentConfiguration.class);
        context.refresh();
        return context;
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
