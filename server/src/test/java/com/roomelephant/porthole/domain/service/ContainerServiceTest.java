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
        var labels = Map.of("custom.metadata", "retained", "com.docker.compose.project", "project");
        var app = container("project-app", 8080, labels);
        when(listContainersCmd.exec()).thenReturn(List.of(app));

        var result =
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
        verify(listContainersCmd).withShowAll(false);
    }

    @Test
    void shouldIncludeStoppedContainersWhenRequested() {
        when(listContainersCmd.exec()).thenReturn(List.of());
        assertThat(containerService.getContainers(true, true, "localhost")).isEmpty();
        verify(listContainersCmd).withShowAll(true);
    }

    @Test
    void shouldFilterAfterEnrichmentAndRetainLabelOnlyContainers() {
        var labels = Map.of("traefik.http.routers.app.rule", "Host(`app.example.com`)");
        var proxy = container("proxy", null, labels);
        var internal = container("internal", null, Map.of());
        when(listContainersCmd.exec()).thenReturn(List.of(proxy, internal));

        var results = containerService.getContainers(false, false, "localhost");

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
        var internal = container("internal", null, Map.of());
        when(listContainersCmd.exec()).thenReturn(List.of(internal));
        var result = containerService.getContainers(true, false, "localhost").getFirst();
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
        var thrown =
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
        var app = container("app", 8080, Map.of());
        when(listContainersCmd.exec()).thenReturn(List.of(app));
        var result = service("docker.example.com")
                .getContainers(false, false, "proxy.example.com")
                .getFirst();
        assertThat(result.urlCandidates())
                .containsExactly(
                        new Endpoint("published-port:8080", "published-port", "http://docker.example.com:8080/"));
    }

    @Test
    void shouldResolveIndependentContextsForEachRequestHostname() {
        var app = container("app", 8080, Map.of());
        when(listContainersCmd.exec()).thenReturn(List.of(app));
        var first = containerService.getContainers(false, false, "192.168.50.6").getFirst();
        var second = containerService
                .getContainers(false, false, "other.example.com")
                .getFirst();
        assertThat(first.urlCandidates())
                .containsExactly(new Endpoint("published-port:8080", "published-port", "http://192.168.50.6:8080/"));
        assertThat(second.urlCandidates())
                .containsExactly(
                        new Endpoint("published-port:8080", "published-port", "http://other.example.com:8080/"));
    }

    private ContainerService service(String publishedHost) {
        var icon = mock(IconComponent.class, invocation -> "icon");
        var discovery = new EndpointDiscovery(
                List.of(new PublishedPortEndpointProvider(), new TraefikEndpointProvider(new TraefikRuleParser())));
        return new ContainerService(
                dockerClient,
                new ContainerMapper(icon),
                new ContainerEnrichment(discovery),
                new EndpointContextResolver(new EndpointProperties(publishedHost)));
    }

    private Container container(String name, Integer publicPort, Map<String, String> labels) {
        var container = mock(Container.class);
        when(container.getId()).thenReturn(name + "-id");
        when(container.getNames()).thenReturn(new String[] {"/" + name});
        when(container.getImage()).thenReturn("nginx:latest");
        when(container.getLabels()).thenReturn(labels);
        when(container.getState()).thenReturn("running");
        when(container.getStatus()).thenReturn("Up");
        if (publicPort != null) {
            var port = mock(ContainerPort.class);
            when(port.getPublicPort()).thenReturn(publicPort);
            when(container.getPorts()).thenReturn(new ContainerPort[] {port});
        }
        return container;
    }
}
