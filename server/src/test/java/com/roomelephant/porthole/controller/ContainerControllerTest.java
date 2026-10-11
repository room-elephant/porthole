package com.roomelephant.porthole.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.roomelephant.porthole.domain.model.ContainerDTO;
import com.roomelephant.porthole.domain.model.EndpointDTO;
import com.roomelephant.porthole.domain.model.VersionDTO;
import com.roomelephant.porthole.domain.service.ContainerService;
import com.roomelephant.porthole.domain.service.VersionService;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContainerController.class)
@DisplayName("ContainerController")
class ContainerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContainerService containerService;

    @MockitoBean
    private VersionService versionService;

    @Nested
    @DisplayName("GET /api/containers")
    class GetContainers {

        @Test
        @DisplayName("should return containers with default parameters")
        void shouldReturnContainersWithDefaultParameters() throws Exception {
            ContainerDTO dto1 = createContainerDTO("container1");
            ContainerDTO dto2 = createContainerDTO("container2");
            List<ContainerDTO> containerDTOs = List.of(dto1, dto2);

            when(containerService.getContainers(false, false, "localhost")).thenReturn(containerDTOs);

            mockMvc.perform(get("/api/containers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("container1"))
                    .andExpect(jsonPath("$[1].name").value("container2"));

            verify(containerService).getContainers(false, false, "localhost");
        }

        @Test
        @DisplayName("should pass includeWithoutEndpoints parameter")
        void shouldPassIncludeWithoutEndpointsParameter() throws Exception {
            ContainerDTO dtoWithPort = createContainerDTO("c1");
            ContainerDTO dtoNoPort = createContainerDTO("c2", Collections.emptySet());

            when(containerService.getContainers(true, false, "localhost")).thenReturn(List.of(dtoWithPort, dtoNoPort));

            mockMvc.perform(get("/api/containers").param("includeWithoutEndpoints", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));

            verify(containerService).getContainers(true, false, "localhost");
        }

        @Test
        @DisplayName("should pass includeStopped parameter")
        void shouldPassIncludeStoppedParameter() throws Exception {
            when(containerService.getContainers(false, true, "localhost")).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/containers").param("includeStopped", "true"))
                    .andExpect(status().isOk());

            verify(containerService).getContainers(false, true, "localhost");
        }

        @Test
        @DisplayName("should pass both parameters")
        void shouldPassBothParameters() throws Exception {
            when(containerService.getContainers(true, true, "localhost")).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/containers")
                            .param("includeWithoutEndpoints", "true")
                            .param("includeStopped", "true"))
                    .andExpect(status().isOk());

            verify(containerService).getContainers(true, true, "localhost");
        }

        @Test
        @DisplayName("should return empty array when no containers")
        void shouldReturnEmptyArrayWhenNoContainers() throws Exception {
            when(containerService.getContainers(false, false, "localhost")).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/containers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/containers/{containerId}/version")
    class GetVersion {

        @Test
        @DisplayName("should return version info")
        void shouldReturnVersionInfo() throws Exception {
            VersionDTO version = new VersionDTO("1.0.0", "1.1.0", true);
            when(versionService.getVersionInfo("container-123")).thenReturn(version);

            mockMvc.perform(get("/api/containers/container-123/version"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentVersion").value("1.0.0"))
                    .andExpect(jsonPath("$.latestVersion").value("1.1.0"))
                    .andExpect(jsonPath("$.updateAvailable").value(true));

            verify(versionService).getVersionInfo("container-123");
        }

        @Test
        @DisplayName("should handle container with no update available")
        void shouldHandleContainerWithNoUpdateAvailable() throws Exception {
            VersionDTO version = new VersionDTO("2.0.0", "2.0.0", false);
            when(versionService.getVersionInfo("container-456")).thenReturn(version);

            mockMvc.perform(get("/api/containers/container-456/version"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updateAvailable").value(false));
        }
    }

    @Test
    void shouldSerializeEndpointContractOnly() throws Exception {
        when(containerService.getContainers(false, false, "localhost")).thenReturn(List.of(createContainerDTO("app")));
        mockMvc.perform(get("/api/containers"))
                .andExpect(jsonPath("$[0].endpoints[0].id").value("published-port:80"))
                .andExpect(jsonPath("$[0].endpoints[0].source").value("published-port"))
                .andExpect(jsonPath("$[0].endpoints[0].url").value("http://localhost/"))
                .andExpect(jsonPath("$[0].hasEndpoints").value(true))
                .andExpect(jsonPath("$[0].exposedPorts").doesNotExist())
                .andExpect(jsonPath("$[0].hasPublicPorts").doesNotExist())
                .andExpect(jsonPath("$[0].labels").doesNotExist())
                .andExpect(jsonPath("$[0].endpoints[0].port").doesNotExist());
    }

    @Test
    void shouldPassEachRequestHostnameToService() throws Exception {
        when(containerService.getContainers(eq(false), eq(false), anyString())).thenAnswer(call -> {
            var hostname = call.getArgument(2, String.class);
            return List.of(createContainerDTO(
                    "app",
                    List.of(new EndpointDTO(
                            "published-port:51413", "published-port", "http://" + hostname + ":51413/"))));
        });
        for (var hostname : List.of("192.168.50.6", "other.example.com")) {
            mockMvc.perform(get("http://" + hostname + ":9753/api/containers")
                            .header("X-Forwarded-Host", "ignored.example.com"))
                    .andExpect(jsonPath("$[0].endpoints[0].url").value("http://" + hostname + ":51413/"))
                    .andExpect(jsonPath("$[0].endpoints[0].id").value("published-port:51413"));
        }
    }

    @Test
    void shouldNotAliasOldQueryParameter() throws Exception {
        when(containerService.getContainers(false, false, "localhost")).thenReturn(List.of());
        mockMvc.perform(get("/api/containers").param("includeWithoutPorts", "true"))
                .andExpect(status().isOk());
        verify(containerService).getContainers(false, false, "localhost");
    }

    private ContainerDTO createContainerDTO(String name, List<EndpointDTO> endpoints) {
        return new ContainerDTO(
                name + "-id", name, name, "nginx:latest", endpoints, "icon", "test-project", "running", "Up");
    }

    private ContainerDTO createContainerDTO(String name) {
        return createContainerDTO(name, Set.of(80));
    }

    private ContainerDTO createContainerDTO(String name, Set<Integer> ports) {
        return new ContainerDTO(
                name + "-id",
                name,
                name,
                "nginx:latest",
                ports.stream()
                        .map(port -> new EndpointDTO("published-port:" + port, "published-port", "http://localhost/"))
                        .toList(),
                "https://example.com/nginx.png",
                "test-project",
                "running",
                "Up 1 hour");
    }
}
