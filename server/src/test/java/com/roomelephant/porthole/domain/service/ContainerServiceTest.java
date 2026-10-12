package com.roomelephant.porthole.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.roomelephant.porthole.config.properties.EndpointProperties;
import com.roomelephant.porthole.domain.component.ContainerEnrichment;
import com.roomelephant.porthole.domain.component.IconComponent;
import com.roomelephant.porthole.domain.component.endpoint.*;
import com.roomelephant.porthole.domain.component.endpoint.EndpointDiscovery;
import com.roomelephant.porthole.domain.component.enrichment.ContainerStateFilter;
import com.roomelephant.porthole.domain.component.enrichment.EndpointAvailabilityFilter;
import com.roomelephant.porthole.domain.component.enrichment.UrlCandidateEnricher;
import com.roomelephant.porthole.domain.mapper.ContainerMapper;
import com.roomelephant.porthole.domain.model.Endpoint;
import com.roomelephant.porthole.domain.model.exception.DockerUnavailableException;
import com.roomelephant.porthole.domain.model.exception.UnexpectedException;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContainerServiceTest {
    @Mock
    private DockerClient dockerClient;

    @Mock
    private ListContainersCmd listContainersCmd;

    private ContainerService containerService;

    @BeforeEach
    void setUp() {
        containerService = service(null);
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(anyBoolean())).thenReturn(listContainersCmd);
    }

    @Test
    void shouldMapAndEnrichDockerContainersBeforeReturningDomainModels() {
        Map<String, String> labels = Map.of("custom.metadata", "retained", "com.docker.compose.project", "project");
        Container app = container("project-app", 8080, labels);
        when(listContainersCmd.exec()).thenReturn(List.of(app));

        com.roomelephant.porthole.domain.model.Container result =
                containerService.getContainers(false, false, "192.168.50.6").getFirst();

        assertThat(result.id()).isEqualTo("project-app-id");
        assertThat(result.name()).isEqualTo("project-app");
        assertThat(result.displayName()).isEqualTo("app");
        assertThat(result.image()).isEqualTo("nginx:latest");
        assertThat(result.iconUrl()).isEqualTo("icon");
        assertThat(result.project()).isEqualTo("project");
        assertThat(result.state()).isEqualTo("running");
        assertThat(result.status()).isEqualTo("Up");
        assertThat(result.labels()).isEqualTo(labels);
        assertThat(result.publishedPorts()).isEqualTo(Set.of(8080));
        assertThat(result.urlCandidates())
                .containsExactly(new Endpoint("published-port:8080", "published-port", "http://192.168.50.6:8080/"));
        verify(listContainersCmd).withShowAll(true);
    }

    @Test
    void shouldRemoveReplicaSuffixWhenDisplayNameIsUniqueWithinProject() {
        Container sonarr = container("smh-sonarr-1", 8080, Map.of("com.docker.compose.project", "smh"));
        Container worker = container("base-authentik-worker-12", 8081, Map.of("com.docker.compose.project", "base"));
        when(listContainersCmd.exec()).thenReturn(List.of(sonarr, worker));

        assertThat(containerService.getContainers(false, false, "localhost"))
                .extracting(com.roomelephant.porthole.domain.model.Container::displayName)
                .containsExactly("sonarr", "authentik-worker");
    }

    @Test
    void shouldKeepReplicaSuffixForCollisionsWithinProject() {
        Map<String, String> labels = Map.of("com.docker.compose.project", "smh");
        List<Container> containers = List.of(
                container("smh-sonarr-1", 8080, labels),
                container("smh-sonarr-2", 8081, labels),
                container("smh-radarr-1", 8082, labels));
        when(listContainersCmd.exec()).thenReturn(containers);

        assertThat(containerService.getContainers(false, false, "localhost"))
                .extracting(com.roomelephant.porthole.domain.model.Container::displayName)
                .containsExactly("sonarr-1", "sonarr-2", "radarr");
    }

    @Test
    void shouldKeepSuffixWhenRestoringAnotherReplicaCreatesACollision() {
        Map<String, String> labels = Map.of("com.docker.compose.project", "smh");
        List<Container> containers = List.of(
                container("smh-web-1", 8080, labels),
                container("smh-web-2", 8081, labels),
                container("smh-web-1-1", 8082, labels));
        when(listContainersCmd.exec()).thenReturn(containers);

        assertThat(containerService.getContainers(false, false, "localhost"))
                .extracting(com.roomelephant.porthole.domain.model.Container::displayName)
                .containsExactly("web-1", "web-2", "web-1-1");
    }

    @Test
    void shouldAllowSameShortNameInDifferentProjects() {
        List<Container> containers = List.of(
                container("smh-sonarr-1", 8080, Map.of("com.docker.compose.project", "smh")),
                container("other-sonarr-1", 8081, Map.of("com.docker.compose.project", "other")));
        when(listContainersCmd.exec()).thenReturn(containers);

        assertThat(containerService.getContainers(false, false, "localhost"))
                .extracting(com.roomelephant.porthole.domain.model.Container::displayName)
                .containsExactly("sonarr", "sonarr");
    }

    @Test
    void shouldKeepSuffixWhenShortNameCollidesWithUnsuffixedName() {
        Map<String, String> labels = Map.of("com.docker.compose.project", "smh");
        List<Container> containers =
                List.of(container("smh-sonarr", 8080, labels), container("smh-sonarr-1", 8081, labels));
        when(listContainersCmd.exec()).thenReturn(containers);

        assertThat(containerService.getContainers(false, false, "localhost"))
                .extracting(com.roomelephant.porthole.domain.model.Container::displayName)
                .containsExactly("sonarr", "sonarr-1");
    }

    @Test
    void shouldPreserveCustomNamesAndNonnumericSuffixes() {
        Map<String, String> labels = Map.of("com.docker.compose.project", "smh");
        List<Container> containers = List.of(
                container("standalone-1", 8080, Map.of()),
                container("custom-1", 8081, labels),
                container("smh-authentik-worker", 8082, labels));
        when(listContainersCmd.exec()).thenReturn(containers);

        assertThat(containerService.getContainers(false, false, "localhost"))
                .extracting(com.roomelephant.porthole.domain.model.Container::displayName)
                .containsExactly("standalone-1", "custom-1", "authentik-worker");
    }

    @Test
    void shouldKeepNamesStableWhenCollidingReplicaIsFilteredOut() {
        Map<String, String> labels = Map.of("com.docker.compose.project", "smh");
        Container hidden = container("smh-sonarr-2", null, labels);
        when(hidden.getState()).thenReturn("exited");
        Container visible = container("smh-sonarr-1", 8080, labels);
        when(listContainersCmd.exec()).thenReturn(List.of(visible, hidden));

        assertThat(containerService.getContainers(false, false, "localhost"))
                .extracting(com.roomelephant.porthole.domain.model.Container::displayName)
                .containsExactly("sonarr-1");
        assertThat(containerService.getContainers(true, true, "localhost"))
                .extracting(com.roomelephant.porthole.domain.model.Container::displayName)
                .containsExactly("sonarr-1", "sonarr-2");
    }

    @Test
    void shouldApplyStoppedFilteringInsideTheChainUsingRequestFlags() {
        Container app = container("stopped", 8080, Map.of());
        when(app.getState()).thenReturn("exited");
        when(listContainersCmd.exec()).thenReturn(List.of(app));
        assertThat(containerService.getContainers(false, false, "localhost")).isEmpty();
        assertThat(containerService.getContainers(false, true, "localhost"))
                .extracting(com.roomelephant.porthole.domain.model.Container::name)
                .containsExactly("stopped");
    }

    @Test
    void shouldIncludeStoppedContainersWhenRequested() {
        when(listContainersCmd.exec()).thenReturn(List.of());
        assertThat(containerService.getContainers(true, true, "localhost")).isEmpty();
        verify(listContainersCmd).withShowAll(true);
    }

    @Test
    void shouldFilterAfterEnrichmentAndRetainLabelOnlyContainers() {
        Map<String, String> labels = Map.of("traefik.http.routers.app.rule", "Host(`app.example.com`)");
        Container proxy = container("proxy", null, labels);
        Container internal = container("internal", null, Map.of());
        when(listContainersCmd.exec()).thenReturn(List.of(proxy, internal));

        List<com.roomelephant.porthole.domain.model.Container> results =
                containerService.getContainers(false, false, "localhost");

        assertThat(results)
                .extracting(com.roomelephant.porthole.domain.model.Container::name)
                .containsExactly("proxy");
        assertThat(results.getFirst().labels()).isEqualTo(labels);
        assertThat(results.getFirst().publishedPorts()).isEmpty();
        assertThat(results.getFirst().urlCandidates())
                .containsExactly(new Endpoint("traefik:http://app.example.com/", "traefik", "http://app.example.com/"));
    }

    @Test
    void shouldIncludeContainersWithoutCandidatesWhenRequested() {
        Container internal = container("internal", null, Map.of());
        when(listContainersCmd.exec()).thenReturn(List.of(internal));
        com.roomelephant.porthole.domain.model.Container result =
                containerService.getContainers(true, false, "localhost").getFirst();
        assertThat(result.name()).isEqualTo("internal");
        assertThat(result.urlCandidates()).isEmpty();
    }

    @Test
    void shouldThrowDockerUnavailableExceptionForConnectionFailures() {
        when(listContainersCmd.exec())
                .thenThrow(new RuntimeException("Connection failed", new SocketException("Connection refused")));
        assertThrows(DockerUnavailableException.class, () -> containerService.getContainers(true, false, "localhost"));
    }

    @Test
    void shouldWrapOtherDockerFailuresAsUnexpectedExceptions() {
        when(listContainersCmd.exec()).thenThrow(new RuntimeException("Some other error"));
        UnexpectedException thrown =
                assertThrows(UnexpectedException.class, () -> containerService.getContainers(true, false, "localhost"));
        assertThat(thrown.getCause()).hasMessage("Some other error");
    }

    @Test
    void shouldReturnEmptyListWhenDockerHasNoContainers() {
        when(listContainersCmd.exec()).thenReturn(List.of());
        assertThat(containerService.getContainers(true, false, "localhost")).isEmpty();
    }

    @Test
    void shouldApplyPublishedHostOverrideDuringEnrichment() {
        Container app = container("app", 8080, Map.of());
        when(listContainersCmd.exec()).thenReturn(List.of(app));
        com.roomelephant.porthole.domain.model.Container result = service("docker.example.com")
                .getContainers(false, false, "proxy.example.com")
                .getFirst();
        assertThat(result.urlCandidates())
                .containsExactly(
                        new Endpoint("published-port:8080", "published-port", "http://docker.example.com:8080/"));
    }

    @Test
    void shouldResolveIndependentContextsForEachRequestHostname() {
        Container app = container("app", 8080, Map.of());
        when(listContainersCmd.exec()).thenReturn(List.of(app));
        com.roomelephant.porthole.domain.model.Container first =
                containerService.getContainers(false, false, "192.168.50.6").getFirst();
        com.roomelephant.porthole.domain.model.Container second = containerService
                .getContainers(false, false, "other.example.com")
                .getFirst();
        assertThat(first.urlCandidates())
                .containsExactly(new Endpoint("published-port:8080", "published-port", "http://192.168.50.6:8080/"));
        assertThat(second.urlCandidates())
                .containsExactly(
                        new Endpoint("published-port:8080", "published-port", "http://other.example.com:8080/"));
    }

    private ContainerService service(String publishedHost) {
        IconComponent icon = mock(IconComponent.class, invocation -> "icon");
        EndpointDiscovery discovery = new EndpointDiscovery(List.of(
                new PublishedPortEndpointProvider(new PublishedHostResolver(new EndpointProperties(publishedHost))),
                new TraefikEndpointProvider(new TraefikRuleParser())));
        return new ContainerService(
                dockerClient,
                new ContainerMapper(icon),
                new ContainerEnrichment(List.of(
                        new ContainerStateFilter(),
                        new UrlCandidateEnricher(discovery),
                        new EndpointAvailabilityFilter())));
    }

    private Container container(String name, Integer publicPort, Map<String, String> labels) {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn(name + "-id");
        when(container.getNames()).thenReturn(new String[] {"/" + name});
        when(container.getImage()).thenReturn("nginx:latest");
        when(container.getLabels()).thenReturn(labels);
        when(container.getState()).thenReturn("running");
        when(container.getStatus()).thenReturn("Up");
        if (publicPort != null) {
            ContainerPort port = mock(ContainerPort.class);
            when(port.getPublicPort()).thenReturn(publicPort);
            when(container.getPorts()).thenReturn(new ContainerPort[] {port});
        }
        return container;
    }
}
