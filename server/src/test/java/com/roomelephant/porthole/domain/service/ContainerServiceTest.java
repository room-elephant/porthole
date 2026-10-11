package com.roomelephant.porthole.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.roomelephant.porthole.domain.mapper.ContainerMapper;
import com.roomelephant.porthole.domain.model.ContainerDTO;
import com.roomelephant.porthole.domain.model.EndpointContext;
import com.roomelephant.porthole.domain.model.EndpointDTO;
import com.roomelephant.porthole.domain.model.exception.DockerUnavailableException;
import com.roomelephant.porthole.domain.model.exception.UnexpectedException;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContainerService")
class ContainerServiceTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private ContainerMapper containerMapper;

    @Mock
    private ListContainersCmd listContainersCmd;

    private ContainerService containerService;
    private final EndpointContext context = new EndpointContext("localhost");

    @BeforeEach
    void setUp() {
        containerService = new ContainerService(dockerClient, containerMapper);
    }

    @Nested
    @DisplayName("getContainers")
    class GetContainers {

        @Test
        @DisplayName("should get containers with showAll=false")
        void shouldGetContainersWithShowAllFalse() {
            Container container1 = createMockContainer();
            Container container2 = createMockContainer();
            ContainerDTO dto1 = createContainerDTO("container1", Set.of(8080));
            ContainerDTO dto2 = createContainerDTO("container2", Set.of(9090));

            when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
            when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
            when(listContainersCmd.exec()).thenReturn(List.of(container1, container2));
            when(containerMapper.toDTO(container1, context)).thenReturn(dto1);
            when(containerMapper.toDTO(container2, context)).thenReturn(dto2);

            List<ContainerDTO> result = containerService.getContainers(true, false, context);

            assertEquals(2, result.size());
            verify(listContainersCmd).withShowAll(false);
            verify(containerMapper).toDTO(container1, context);
            verify(containerMapper).toDTO(container2, context);
        }

        @Test
        @DisplayName("should include stopped containers when includeStopped is true")
        void shouldIncludeStoppedContainersWhenIncludeStoppedIsTrue() {
            when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
            when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
            when(listContainersCmd.exec()).thenReturn(Collections.emptyList());

            containerService.getContainers(true, true, context);

            verify(listContainersCmd).withShowAll(true);
        }

        @Test
        @DisplayName("should filter containers without endpoints when includeWithoutEndpoints is false")
        void shouldFilterContainersWithoutEndpoints() {
            Container container1 = createMockContainer();
            Container container2 = createMockContainer();
            ContainerDTO dtoWithPorts = createContainerDTO("c1", Set.of(8080));
            ContainerDTO dtoNoPorts = createContainerDTO("c2", Set.of());

            when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
            when(listContainersCmd.withShowAll(anyBoolean())).thenReturn(listContainersCmd);
            when(listContainersCmd.exec()).thenReturn(List.of(container1, container2));
            when(containerMapper.toDTO(container1, context)).thenReturn(dtoWithPorts);
            when(containerMapper.toDTO(container2, context)).thenReturn(dtoNoPorts);

            List<ContainerDTO> result = containerService.getContainers(false, false, context);

            assertEquals(1, result.size());
            assertEquals(dtoWithPorts, result.get(0));
        }

        @Test
        @DisplayName("should throw DockerUnavailableException when Docker is not reachable")
        void shouldThrowDockerUnavailableExceptionWhenDockerIsNotReachable() {
            RuntimeException dockerException =
                    new RuntimeException("Connection failed", new SocketException("Connection refused"));

            when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
            when(listContainersCmd.withShowAll(anyBoolean())).thenReturn(listContainersCmd);
            when(listContainersCmd.exec()).thenThrow(dockerException);

            assertThrows(DockerUnavailableException.class, () -> containerService.getContainers(true, false, context));
        }

        @Test
        @DisplayName("should throw UnexpectedException for non-connection RuntimeExceptions")
        void shouldThrowUnexpectedExceptionForNonConnectionRuntimeExceptions() {
            RuntimeException otherException = new RuntimeException("Some other error");

            when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
            when(listContainersCmd.withShowAll(anyBoolean())).thenReturn(listContainersCmd);
            when(listContainersCmd.exec()).thenThrow(otherException);

            UnexpectedException thrown =
                    assertThrows(UnexpectedException.class, () -> containerService.getContainers(true, false, context));
            assertEquals("Some other error", thrown.getCause().getMessage());
        }

        @Test
        @DisplayName("should return empty list when no containers exist")
        void shouldReturnEmptyListWhenNoContainersExist() {
            when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
            when(listContainersCmd.withShowAll(anyBoolean())).thenReturn(listContainersCmd);
            when(listContainersCmd.exec()).thenReturn(Collections.emptyList());

            List<ContainerDTO> result = containerService.getContainers(true, true, context);

            assertTrue(result.isEmpty());
        }
    }

    @Test
    void shouldKeepLabelOnlyContainerAfterDiscovery() {
        var container = mock(Container.class);
        when(container.getId()).thenReturn("label-only");
        when(container.getNames()).thenReturn(new String[] {"/label-only"});
        when(container.getImage()).thenReturn("app:latest");
        when(container.getLabels())
                .thenReturn(java.util.Map.of("traefik.http.routers.app.rule", "Host(`app.example.com`)"));
        var icon = mock(com.roomelephant.porthole.domain.component.IconComponent.class);
        when(icon.resolveIcon("app")).thenReturn("icon");
        var discovery = new com.roomelephant.porthole.domain.component.endpoint.EndpointDiscovery(List.of(
                new com.roomelephant.porthole.domain.component.endpoint.PublishedPortEndpointProvider(),
                new com.roomelephant.porthole.domain.component.endpoint.TraefikEndpointProvider(
                        new com.roomelephant.porthole.domain.component.endpoint.TraefikRuleParser())));
        var service = new ContainerService(dockerClient, new ContainerMapper(icon, discovery));
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));
        assertEquals(
                "http://app.example.com/",
                service.getContainers(false, false, context)
                        .getFirst()
                        .endpoints()
                        .getFirst()
                        .url());
    }

    private Container createMockContainer() {
        return mock(Container.class);
    }

    private ContainerDTO createContainerDTO(String name, Set<Integer> ports) {
        return new ContainerDTO(
                name + "-id",
                name,
                name,
                "nginx:latest",
                ports.stream()
                        .map(port -> new EndpointDTO(
                                "published-port:" + port, "published-port", "http://localhost:" + port + "/"))
                        .toList(),
                "https://example.com/nginx.png",
                null,
                "running",
                "Up 2 hours");
    }
}
