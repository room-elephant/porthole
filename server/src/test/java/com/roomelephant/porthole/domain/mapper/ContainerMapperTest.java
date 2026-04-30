package com.roomelephant.porthole.domain.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.roomelephant.porthole.domain.component.IconComponent;
import com.roomelephant.porthole.domain.model.ContainerDTO;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContainerMapper")
class ContainerMapperTest {

    @Mock
    private IconComponent iconComponent;

    private ContainerMapper containerMapper;

    @BeforeEach
    void setUp() {
        containerMapper = new ContainerMapper(iconComponent);
    }

    @Nested
    @DisplayName("toDTO")
    class ToDTO {

        @Test
        @DisplayName("should map container to DTO with all fields")
        void shouldMapContainerToDTOWithAllFields() {
            Container container = createMockContainer(
                    "abc123",
                    "/my-container",
                    "nginx:1.25",
                    new Integer[] {80, 443},
                    Map.of("com.docker.compose.project", "my-project"),
                    "running",
                    "Up 2 hours");
            when(iconComponent.resolveIcon("nginx")).thenReturn("https://example.com/nginx.png");

            ContainerDTO dto = containerMapper.toDTO(container);

            assertEquals("abc123", dto.id());
            assertEquals("my-container", dto.name());
            assertEquals("nginx:1.25", dto.image());
            assertEquals(2, dto.exposedPorts().size());
            assertTrue(dto.exposedPorts().contains(80));
            assertTrue(dto.exposedPorts().contains(443));
            assertEquals("https://example.com/nginx.png", dto.iconUrl());
            assertEquals("my-project", dto.project());
            assertEquals("running", dto.state());
            assertEquals("Up 2 hours", dto.status());
        }

        @Test
        @DisplayName("should handle container with no public ports")
        void shouldHandleContainerWithNoPublicPorts() {
            Container container = createMockContainer(
                    "abc123", "/my-container", "redis:7", new Integer[] {}, null, "running", "Up 1 hour");
            when(iconComponent.resolveIcon("redis")).thenReturn("https://example.com/redis.png");

            ContainerDTO dto = containerMapper.toDTO(container);

            assertTrue(dto.exposedPorts().isEmpty());
            assertFalse(dto.hasPublicPorts());
        }

        @Test
        @DisplayName("should filter null ports")
        void shouldFilterNullPorts() {
            Container container = mock(Container.class);
            when(container.getId()).thenReturn("abc123");
            when(container.getNames()).thenReturn(new String[] {"/my-container"});
            when(container.getImage()).thenReturn("nginx:latest");
            when(container.getLabels()).thenReturn(null);
            when(container.getState()).thenReturn("running");
            when(container.getStatus()).thenReturn("Up 1 hour");

            ContainerPort port1 = mock(ContainerPort.class);
            when(port1.getPublicPort()).thenReturn(80);
            ContainerPort port2 = mock(ContainerPort.class);
            when(port2.getPublicPort()).thenReturn(null);
            when(container.getPorts()).thenReturn(new ContainerPort[] {port1, port2});

            when(iconComponent.resolveIcon(anyString())).thenReturn("https://example.com/icon.png");

            ContainerDTO dto = containerMapper.toDTO(container);

            assertEquals(1, dto.exposedPorts().size());
            assertTrue(dto.exposedPorts().contains(80));
        }

        @Test
        @DisplayName("should deduplicate ports")
        void shouldDeduplicatePorts() {
            Container container = createMockContainer(
                    "abc123",
                    "/my-container",
                    "nginx:latest",
                    new Integer[] {80, 80, 443, 443},
                    null,
                    "running",
                    "Up 1 hour");
            when(iconComponent.resolveIcon(anyString())).thenReturn("https://example.com/icon.png");

            ContainerDTO dto = containerMapper.toDTO(container);

            assertEquals(2, dto.exposedPorts().size());
        }

        @Test
        @DisplayName("should strip project prefix from display name")
        void shouldStripProjectPrefixFromDisplayName() {
            Container container = createMockContainer(
                    "abc123",
                    "/my-project-web",
                    "nginx:latest",
                    new Integer[] {80},
                    Map.of("com.docker.compose.project", "my-project"),
                    "running",
                    "Up 1 hour");
            when(iconComponent.resolveIcon(anyString())).thenReturn("https://example.com/icon.png");

            ContainerDTO dto = containerMapper.toDTO(container);

            assertEquals("my-project-web", dto.name());
            assertEquals("web", dto.displayName());
        }

        @Test
        @DisplayName("should not strip prefix when name does not start with project")
        void shouldNotStripPrefixWhenNameDoesNotStartWithProject() {
            Container container = createMockContainer(
                    "abc123",
                    "/other-web",
                    "nginx:latest",
                    new Integer[] {80},
                    Map.of("com.docker.compose.project", "my-project"),
                    "running",
                    "Up 1 hour");
            when(iconComponent.resolveIcon(anyString())).thenReturn("https://example.com/icon.png");

            ContainerDTO dto = containerMapper.toDTO(container);

            assertEquals("other-web", dto.name());
            assertEquals("other-web", dto.displayName());
        }

        @Test
        @DisplayName("should use name as display name when no project")
        void shouldUseNameAsDisplayNameWhenNoProject() {
            Container container = createMockContainer(
                    "abc123", "/standalone-app", "myapp:latest", new Integer[] {8080}, null, "running", "Up 1 hour");
            when(iconComponent.resolveIcon(anyString())).thenReturn("https://example.com/icon.png");

            ContainerDTO dto = containerMapper.toDTO(container);

            assertEquals("standalone-app", dto.name());
            assertEquals("standalone-app", dto.displayName());
        }

        @Test
        @DisplayName("should handle empty names array")
        void shouldHandleEmptyNamesArray() {
            Container container = mock(Container.class);
            when(container.getId()).thenReturn("abc123");
            when(container.getNames()).thenReturn(new String[] {});
            when(container.getImage()).thenReturn("nginx:latest");
            when(container.getLabels()).thenReturn(null);
            when(container.getState()).thenReturn("running");
            when(container.getStatus()).thenReturn("Up 1 hour");
            when(container.getPorts()).thenReturn(new ContainerPort[] {});
            when(iconComponent.resolveIcon(anyString())).thenReturn("https://example.com/icon.png");

            ContainerDTO dto = containerMapper.toDTO(container);

            assertEquals("Unknown", dto.name());
        }

        @Test
        @DisplayName("should extract image name correctly for icon resolution")
        void shouldExtractImageNameCorrectlyForIconResolution() {
            Container container = createMockContainer(
                    "abc123",
                    "/my-container",
                    "bitnami/postgresql:15",
                    new Integer[] {5432},
                    null,
                    "running",
                    "Up 1 hour");
            when(iconComponent.resolveIcon("postgresql")).thenReturn("https://example.com/postgresql.png");

            ContainerDTO dto = containerMapper.toDTO(container);

            assertEquals("https://example.com/postgresql.png", dto.iconUrl());
        }
    }

    private Container createMockContainer(
            String id,
            String name,
            String image,
            Integer[] ports,
            Map<String, String> labels,
            String state,
            String status) {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn(id);
        when(container.getNames()).thenReturn(new String[] {name});
        when(container.getImage()).thenReturn(image);
        when(container.getLabels()).thenReturn(labels);
        when(container.getState()).thenReturn(state);
        when(container.getStatus()).thenReturn(status);

        ContainerPort[] containerPorts = new ContainerPort[ports.length];
        for (int i = 0; i < ports.length; i++) {
            ContainerPort port = mock(ContainerPort.class);
            when(port.getPublicPort()).thenReturn(ports[i]);
            containerPorts[i] = port;
        }
        when(container.getPorts()).thenReturn(containerPorts);

        return container;
    }
}
