package com.roomelephant.porthole.core.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.config.EnrichmentConfiguration;
import com.roomelephant.porthole.core.enrichment.endpoint.*;
import com.roomelephant.porthole.core.enrichment.icon.*;
import com.roomelephant.porthole.core.enrichment.preferences.CustomIconResolver;
import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.Endpoint;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.endpoints.EndpointDiscovery;
import com.roomelephant.porthole.endpoints.PublishedHostResolver;
import com.roomelephant.porthole.endpoints.PublishedPortEndpointProvider;
import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryService;
import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.entries.Icon;
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
        when(versions.cachedVersion(any())).thenReturn(version);
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
            assertThat(stopped.version()).isNull();
            assertThat(internal.version()).isNull();
        }
    }

    private AnnotationConfigApplicationContext context(VersionRefreshService versions) {
        return context(versions, List.of());
    }

    @Test
    void savedIconOverridesTheResolvedDefaultOnlyForItsContainer() {
        var custom = new EntryRecord(
                new ContainerRef("local", "project-web-1"), null, new Icon.Library("coredns"), null, null);
        try (var context = context(mock(VersionRefreshService.class), List.of(custom))) {
            var chosen = container("project-web-1", "running", Set.of(8080));
            var other = container("project-api-1", "running", Set.of(8081));

            context.getBean(ContainerEnrichment.class)
                    .enrich(List.of(chosen, other), new EnrichmentContext("local", "localhost", false, false));

            assertThat(chosen.iconUrl()).isEqualTo("https://icons.example.com/coredns.svg");
            assertThat(other.iconUrl()).isEqualTo("https://icons.example.com/nginx.svg");
        }
    }

    @Test
    void shouldResolvePreferencesBeforeAvailabilityWithoutBypassingState() {
        var customUrl = new EndpointChoice.Custom("https://worker.example.com");
        var custom = custom("project-worker-1", customUrl);
        var none = custom("project-web-1", EndpointChoice.NONE);
        try (var context = context(
                mock(VersionRefreshService.class), List.of(custom, custom("project-stopped-1", customUrl), none))) {
            var worker = container("project-worker-1", "running", Set.of());
            var stopped = container("project-stopped-1", "exited", Set.of());
            var web = container("project-web-1", "running", Set.of(8080));

            var result = context.getBean(ContainerEnrichment.class)
                    .enrich(List.of(worker, stopped, web), new EnrichmentContext("local", "localhost", false, false));

            assertThat(result).containsExactly(worker, web);
            assertThat(worker.entry()).isEqualTo(custom);
            assertThat(worker.urlCandidates())
                    .containsExactly(new Endpoint("custom", "custom", "https://worker.example.com", true));
            assertThat(worker.iconUrl()).isEqualTo("https://icons.example.com/nginx.svg");
            assertThat(web.entry()).isEqualTo(none);
            assertThat(web.urlCandidates())
                    .containsExactly(
                            new Endpoint("published-port:8080", "published-port", "http://localhost:8080/", false));
            assertThat(stopped.entry()).isNull();
        }
    }

    private AnnotationConfigApplicationContext context(VersionRefreshService versions, List<EntryRecord> records) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(VersionRefreshService.class, () -> versions);
        var entries = mock(EntryService.class);
        when(entries.state()).thenReturn(new EntryState(records));
        context.registerBean(EntryService.class, () -> entries);
        context.registerBean(
                EndpointDiscovery.class,
                () -> new EndpointDiscovery(List.of(new PublishedPortEndpointProvider(new PublishedHostResolver()))));
        var iconSettings = new IconSettings("https://icons.example.com", ".svg");
        context.registerBean(CustomIconResolver.class, () -> new CustomIconResolver(iconSettings));
        context.registerBean(IconResolver.class, () -> new IconResolver(Map.of(), iconSettings));
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

    private static EntryRecord custom(String name, EndpointChoice choice) {
        return new EntryRecord(new ContainerRef("local", name), null, null, choice, null);
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
