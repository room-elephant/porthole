package com.roomelephant.porthole.fleet.docker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ContainerPort;
import com.roomelephant.porthole.fleet.*;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DockerJavaOperationsTest {
    private final DockerClient client = mock(DockerClient.class);
    private final DockerOperations docker =
            new DockerJavaOperations(new DockerSourceRegistry(List.of(new DockerConnection("one", client))));

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
    void listMapsImageId() {
        var cmd = mock(ListContainersCmd.class);
        when(client.listContainersCmd()).thenReturn(cmd);
        when(cmd.withShowAll(true)).thenReturn(cmd);
        var raw = mock(com.github.dockerjava.api.model.Container.class);
        when(raw.getId()).thenReturn("id");
        when(raw.getImageId()).thenReturn("sha256:abc");
        when(cmd.exec()).thenReturn(List.of(raw));
        assertThat(docker.listContainers("one").getFirst().imageId()).isEqualTo("sha256:abc");
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
        assertThat(new com.roomelephant.porthole.versions.InstalledVersionDetector().detect(inspection))
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
        assertThatThrownBy(() -> docker.inspectContainer(key)).isInstanceOf(ContainerNotFoundException.class);
        doThrow(new RuntimeException(new SocketException("private socket")))
                .when(cmd)
                .exec();
        assertThatThrownBy(() -> docker.inspectContainer(key)).isInstanceOf(DockerUnavailableException.class);
        doThrow(new IllegalStateException("bad response")).when(cmd).exec();
        assertThatThrownBy(() -> docker.inspectContainer(key)).isInstanceOf(DockerOperationException.class);
    }

    @Test
    void listFailuresAreClassifiedAtTheBoundary() {
        when(client.listContainersCmd()).thenThrow(new RuntimeException(new SocketException("private socket")));
        assertThatThrownBy(() -> docker.listContainers("one")).isInstanceOf(DockerUnavailableException.class);
        doThrow(new IllegalStateException("bad response")).when(client).listContainersCmd();
        assertThatThrownBy(() -> docker.listContainers("one")).isInstanceOf(DockerOperationException.class);
    }

    @SuppressWarnings("unchecked")
    private com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Event> watch(
            ContainerEventListener listener) {
        var cmd = mock(EventsCmd.class);
        when(client.eventsCmd()).thenReturn(cmd);
        when(cmd.withEventTypeFilter(any(com.github.dockerjava.api.model.EventType[].class)))
                .thenReturn(cmd);
        when(cmd.exec(any())).thenAnswer(call -> call.getArgument(0));
        docker.watchContainerEvents("one", listener);
        var captor = org.mockito.ArgumentCaptor.forClass(com.github.dockerjava.api.async.ResultCallback.Adapter.class);
        verify(cmd).withEventTypeFilter(com.github.dockerjava.api.model.EventType.CONTAINER);
        verify(cmd).exec(captor.capture());
        return captor.getValue();
    }

    @Test
    void watchFiltersContainerEventsAndForwardsEach() {
        var listener = mock(ContainerEventListener.class);
        var callback = watch(listener);
        callback.onNext(new com.github.dockerjava.api.model.Event("start", "id", "nginx", 1L));
        callback.onNext(new com.github.dockerjava.api.model.Event("die", "id", "nginx", 2L));
        verify(listener, times(2)).onEvent();
        verify(listener, never()).onClosed(any());
    }

    @Test
    void watchIgnoresEventsThatCannotChangeTheListing() {
        var listener = mock(ContainerEventListener.class);
        var callback = watch(listener);
        for (String action : new String[] {
            "exec_create: /bin/sh -c curl -f http://localhost/",
            "exec_start: /bin/sh -c curl -f http://localhost/",
            "exec_die",
            "attach",
            "resize",
            "top",
            "archive-path"
        }) {
            callback.onNext(new com.github.dockerjava.api.model.Event(action, "id", "nginx", 1L));
        }
        verify(listener, never()).onEvent();

        callback.onNext(new com.github.dockerjava.api.model.Event("health_status: healthy", "id", "nginx", 2L));
        callback.onNext(new com.github.dockerjava.api.model.Event(null, "id", "nginx", 3L));
        verify(listener, times(2)).onEvent();
    }

    @Test
    void watchReportsStreamErrorAndCompletion() {
        var listener = mock(ContainerEventListener.class);
        var callback = watch(listener);
        var boom = new IllegalStateException("boom");
        callback.onError(boom);
        verify(listener).onClosed(boom);
        var completed = mock(ContainerEventListener.class);
        watch(completed).onComplete();
        verify(completed).onClosed(null);
    }

    @Test
    void closingSubscriptionClosesCallback() throws Exception {
        var cmd = mock(EventsCmd.class);
        var callback = mock(com.github.dockerjava.api.async.ResultCallback.Adapter.class);
        when(client.eventsCmd()).thenReturn(cmd);
        when(cmd.withEventTypeFilter(any(com.github.dockerjava.api.model.EventType[].class)))
                .thenReturn(cmd);
        when(cmd.exec(any())).thenReturn(callback);
        docker.watchContainerEvents("one", mock(ContainerEventListener.class)).close();
        verify(callback).close();
    }

    @Test
    void watchTranslatesTransportFailure() {
        when(client.eventsCmd()).thenThrow(new RuntimeException(new SocketException("private socket")));
        var listener = mock(ContainerEventListener.class);
        assertThatThrownBy(() -> docker.watchContainerEvents("one", listener))
                .isInstanceOf(DockerUnavailableException.class);
    }
}
