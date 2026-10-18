package com.roomelephant.porthole.outbound.docker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ContainerPort;
import com.roomelephant.porthole.core.model.ContainerKey;
import com.roomelephant.porthole.core.model.exception.*;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DockerJavaOperationsTest {
    private final DockerClient client = mock(DockerClient.class);
    private final DockerOperations docker = new DockerJavaOperations(new DockerSourceRegistry(
            List.of(new DockerConnection("one", "One", "host", Duration.ofSeconds(2), Duration.ofSeconds(3), client))));

    @Test
    void sourceDescriptorsContainNoClientsAndKeepConfiguredBudgets() {
        assertThat(docker.sources()).containsExactly(docker.requireSource("one"));
        assertThat(docker.requireSource("one").publishedHost()).isEqualTo("host");
        assertThat(docker.requireSource("one").responseTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThatThrownBy(() -> docker.requireSource("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsStoppedContainersAndNormalizesMetadataWithoutPresentation() {
        var cmd = mock(ListContainersCmd.class);
        when(client.listContainersCmd()).thenReturn(cmd);
        when(cmd.withShowAll(true)).thenReturn(cmd);
        var raw = mock(com.github.dockerjava.api.model.Container.class);
        var port = mock(ContainerPort.class);
        when(raw.getId()).thenReturn("id");
        when(raw.getNames()).thenReturn(new String[] {"/project-app-1"});
        when(raw.getImage()).thenReturn("nginx:1");
        when(raw.getLabels()).thenReturn(Map.of("com.docker.compose.project", "project"));
        when(raw.getPorts()).thenReturn(new ContainerPort[] {port});
        when(port.getPublicPort()).thenReturn(8080);
        when(cmd.exec()).thenReturn(List.of(raw));
        var snapshot = docker.listContainers("one").getFirst();
        assertThat(snapshot.id()).isEqualTo("id");
        assertThat(snapshot.name()).isEqualTo("project-app-1");
        assertThat(snapshot.publishedPorts()).containsExactly(8080);
        assertThat(snapshot.labels()).containsEntry("com.docker.compose.project", "project");
        verify(cmd).withShowAll(true);
    }

    @Test
    void missingListMetadataIsNormalized() {
        var mapper = new DockerContainerMapper();
        var raw = mock(com.github.dockerjava.api.model.Container.class);
        var snapshot = mapper.toSnapshot(raw);
        assertThat(snapshot.id()).isNull();
        assertThat(snapshot.name()).isEqualTo("Unknown");
        assertThat(snapshot.publishedPorts()).isEmpty();
        assertThat(snapshot.labels()).isEmpty();
    }

    @Test
    void missingInspectionConfigAndDigestsStayAbsent() {
        var cmd = mock(InspectContainerCmd.class);
        var response = mock(InspectContainerResponse.class);
        when(client.inspectContainerCmd("id")).thenReturn(cmd);
        when(cmd.exec()).thenReturn(response);
        var inspection = docker.inspectContainer(new ContainerKey("one", "id"));
        assertThat(inspection.image()).isNull();
        assertThat(inspection.environment()).isEmpty();
        assertThat(inspection.labels()).isEmpty();
        var imageCmd = mock(InspectImageCmd.class);
        when(client.inspectImageCmd("image")).thenReturn(imageCmd);
        var imageResponse = mock(InspectImageResponse.class);
        when(imageResponse.getRepoDigests()).thenReturn(null);
        when(imageCmd.exec()).thenReturn(imageResponse);
        assertThat(docker.getRepoDigests("one", "image")).isNull();
    }

    @Test
    void inspectionCopiesEnvironmentAndLabels() {
        var cmd = mock(InspectContainerCmd.class);
        var response = mock(InspectContainerResponse.class);
        var config = mock(ContainerConfig.class);
        when(client.inspectContainerCmd("id")).thenReturn(cmd);
        when(cmd.exec()).thenReturn(response);
        when(response.getConfig()).thenReturn(config);
        when(config.getImage()).thenReturn("nginx:1");
        when(config.getEnv()).thenReturn(new String[] {"VERSION=1"});
        when(config.getLabels()).thenReturn(Map.of("version", "2"));
        var inspection = docker.inspectContainer(new ContainerKey("one", "id"));
        assertThat(inspection.image()).isEqualTo("nginx:1");
        assertThat(inspection.environment()).containsExactly("VERSION=1");
        assertThat(inspection.labels()).containsEntry("version", "2");
    }

    @Test
    void inspectionDoesNotRejectMetadataThatVersionDetectionPreviouslyAccepted() {
        var cmd = mock(InspectContainerCmd.class);
        var response = mock(InspectContainerResponse.class);
        var config = mock(ContainerConfig.class);
        when(client.inspectContainerCmd("id")).thenReturn(cmd);
        when(cmd.exec()).thenReturn(response);
        when(response.getConfig()).thenReturn(config);
        when(config.getImage()).thenReturn("nginx:1");
        var environment = new String[] {"NGINX_VERSION=1", null};
        var labels = new java.util.HashMap<String, String>();
        labels.put("unrelated", null);
        when(config.getEnv()).thenReturn(environment);
        when(config.getLabels()).thenReturn(labels);
        var inspection = docker.inspectContainer(new ContainerKey("one", "id"));
        assertThat(new com.roomelephant.porthole.core.version.InstalledVersionDetector().detect(inspection))
                .isEqualTo("1");
        environment[0] = "NGINX_VERSION=2";
        labels.put("version", "3");
        assertThat(inspection.environment().getFirst()).isEqualTo("NGINX_VERSION=1");
        assertThat(inspection.labels()).containsEntry("unrelated", null).doesNotContainKey("version");
    }

    @Test
    void inspectionTranslatesMissingTransportAndUnexpectedFailures() {
        var cmd = mock(InspectContainerCmd.class);
        when(client.inspectContainerCmd("id")).thenReturn(cmd);
        when(cmd.exec()).thenThrow(new com.github.dockerjava.api.exception.NotFoundException("missing"));
        var key = new ContainerKey("one", "id");
        assertThatThrownBy(() -> docker.inspectContainer(key)).isInstanceOf(NotFoundException.class);
        doThrow(new RuntimeException(new SocketException("private socket")))
                .when(cmd)
                .exec();
        assertThatThrownBy(() -> docker.inspectContainer(key)).isInstanceOf(DockerUnavailableException.class);
        doThrow(new IllegalStateException("bad response")).when(cmd).exec();
        assertThatThrownBy(() -> docker.inspectContainer(key)).isInstanceOf(UnexpectedException.class);
    }

    @Test
    void listFailuresAreClassifiedAtTheBoundary() {
        when(client.listContainersCmd()).thenThrow(new RuntimeException(new SocketException("private socket")));
        assertThatThrownBy(() -> docker.listContainers("one")).isInstanceOf(DockerUnavailableException.class);
        doThrow(new IllegalStateException("bad response")).when(client).listContainersCmd();
        assertThatThrownBy(() -> docker.listContainers("one")).isInstanceOf(UnexpectedException.class);
    }
}
