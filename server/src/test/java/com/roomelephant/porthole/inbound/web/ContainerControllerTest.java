package com.roomelephant.porthole.inbound.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.enrichment.preferences.ContainerPreferencesEnricher;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.ContainerPreferences;
import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.DockerSource;
import com.roomelephant.porthole.core.model.Endpoint;
import com.roomelephant.porthole.core.model.SelectedEndpoint;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.core.port.inbound.GetContainers;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import com.roomelephant.porthole.core.service.ContainerPreferencesService;
import com.roomelephant.porthole.inbound.web.mapper.ContainerDTOMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ContainerController.class, VersionController.class})
@Import({ContainerDTOMapper.class, ContainerPreferencesService.class})
@DisplayName("ContainerController")
class ContainerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContainerPreferencesStore preferencesStore;

    @MockitoBean
    private DockerOperations dockerOperations;

    @BeforeEach
    void saveConfirmedPreferences() {
        when(dockerOperations.sources())
                .thenReturn(List.of(
                        new DockerSource("local", "Local", null, Duration.ofSeconds(1), Duration.ofSeconds(1), true),
                        new DockerSource(
                                "remote", "Remote", null, Duration.ofSeconds(1), Duration.ofSeconds(1), false)));
        when(preferencesStore.replaceSelection(any(), nullable(SelectedEndpoint.class)))
                .thenAnswer(invocation -> new ContainerPreferences(invocation.getArgument(1)));
    }

    @MockitoBean
    private GetContainers containerService;

    @MockitoBean
    private com.roomelephant.porthole.core.port.inbound.RefreshVersions versions;

    @Test
    void acceptsManualVersionRefresh() throws Exception {
        mockMvc.perform(post("/api/versions/refresh"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        verify(versions).requestRefresh();
    }

    @Test
    void doesNotAllowCrossOriginRefreshPreflight() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(
                                "/api/versions/refresh")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(versions);
    }

    @Nested
    @DisplayName("GET /api/containers")
    class GetContainersEndpoint {

        @Test
        @DisplayName("should return containers with default parameters")
        void shouldReturnContainersWithDefaultParameters() throws Exception {
            Container dto1 = createContainer("container1");
            Container dto2 = createContainer("container2");
            List<Container> containerDTOs = List.of(dto1, dto2);

            when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                    .thenReturn(inventory(containerDTOs));

            mockMvc.perform(get("/api/containers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.containers.length()").value(2))
                    .andExpect(jsonPath("$.containers[0].name").value("container1"))
                    .andExpect(jsonPath("$.containers[1].name").value("container2"));

            verify(containerService)
                    .getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false));
        }

        @Test
        @DisplayName("should pass includeWithoutEndpoints parameter")
        void shouldPassIncludeWithoutEndpointsParameter() throws Exception {
            Container dtoWithPort = createContainer("c1");
            Container dtoNoPort = createContainer("c2", Collections.emptySet());

            when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(true, false)))
                    .thenReturn(inventory(List.of(dtoWithPort, dtoNoPort)));

            mockMvc.perform(get("/api/containers").param("includeWithoutEndpoints", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.containers.length()").value(2));

            verify(containerService)
                    .getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(true, false));
        }

        @Test
        @DisplayName("should pass includeStopped parameter")
        void shouldPassIncludeStoppedParameter() throws Exception {
            when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, true)))
                    .thenReturn(inventory(Collections.emptyList()));

            mockMvc.perform(get("/api/containers").param("includeStopped", "true"))
                    .andExpect(status().isOk());

            verify(containerService)
                    .getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, true));
        }

        @Test
        @DisplayName("should pass both parameters")
        void shouldPassBothParameters() throws Exception {
            when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(true, true)))
                    .thenReturn(inventory(Collections.emptyList()));

            mockMvc.perform(get("/api/containers")
                            .param("includeWithoutEndpoints", "true")
                            .param("includeStopped", "true"))
                    .andExpect(status().isOk());

            verify(containerService).getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(true, true));
        }

        @Test
        @DisplayName("should return empty array when no containers")
        void shouldReturnEmptyArrayWhenNoContainers() throws Exception {
            when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                    .thenReturn(inventory(Collections.emptyList()));

            mockMvc.perform(get("/api/containers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.containers.length()").value(0));
        }
    }

    @Test
    void shouldIncludeCachedVersionInContainersResponse() throws Exception {
        Container container = createContainer("app");
        container.setVersion(new VersionInfo("1.0", "2.0", true));
        when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                .thenReturn(inventory(List.of(container)));
        mockMvc.perform(get("/api/containers"))
                .andExpect(jsonPath("$.containers[0].version.currentVersion").value("1.0"))
                .andExpect(jsonPath("$.containers[0].version.latestVersion").value("2.0"))
                .andExpect(jsonPath("$.containers[0].version.updateAvailable").value(true));
    }

    @Test
    void shouldExposeNullVersionBeforeResolution() throws Exception {
        when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                .thenReturn(inventory(List.of(createContainer("app"))));
        mockMvc.perform(get("/api/containers"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"version\":null")));
    }

    @Test
    void shouldRemoveSeparateVersionEndpoint() throws Exception {
        mockMvc.perform(get("/api/containers/anything/version")).andExpect(status().isNotFound());
    }

    @Test
    void shouldSerializeEndpointContractOnly() throws Exception {
        when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                .thenReturn(inventory(List.of(createContainer("app"))));
        mockMvc.perform(get("/api/containers"))
                .andExpect(jsonPath("$.containers[0].sourceId").value("local"))
                .andExpect(jsonPath("$.sources[0].status").value("UP"))
                .andExpect(jsonPath("$.containers[0].endpoints[0].id").value("published-port:80"))
                .andExpect(jsonPath("$.containers[0].endpoints[0].source").value("published-port"))
                .andExpect(jsonPath("$.containers[0].endpoints[0].url").value("http://localhost/"))
                .andExpect(jsonPath("$.containers[0].endpoints[0].selected").value(true))
                .andExpect(jsonPath("$.containers[0].hasEndpoints").value(true))
                .andExpect(jsonPath("$.containers[0].exposedPorts").doesNotExist())
                .andExpect(jsonPath("$.containers[0].hasPublicPorts").doesNotExist())
                .andExpect(jsonPath("$.containers[0].labels").doesNotExist())
                .andExpect(jsonPath("$.containers[0].publishedPorts").doesNotExist())
                .andExpect(jsonPath("$.containers[0].urlCandidates").doesNotExist())
                .andExpect(jsonPath("$.containers[0].endpoints[0].port").doesNotExist());
    }

    @Test
    void shouldIgnoreRequestHostnameAndForwardedHost() throws Exception {
        when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                .thenReturn(inventory(List.of(createContainer("app"))));
        for (String hostname : List.of("192.168.50.6", "other.example.com")) {
            mockMvc.perform(get("http://" + hostname + ":9753/api/containers")
                            .header("Host", "untrusted.example.com")
                            .header("X-Forwarded-Host", "forwarded.example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.containers[0].name").value("app"));
        }
        verify(containerService, times(2))
                .getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false));
    }

    @Test
    void shouldNotAliasOldQueryParameter() throws Exception {
        when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                .thenReturn(inventory(List.of()));
        mockMvc.perform(get("/api/containers").param("includeWithoutPorts", "true"))
                .andExpect(status().isOk());
        verify(containerService).getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false));
    }

    @Test
    void shouldExposeResolvedAvailabilityWithoutPreferencesInInventory() throws Exception {
        when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                .thenReturn(inventory(List.of(createContainer("app"))));
        mockMvc.perform(get("/api/containers"))
                .andExpect(jsonPath("$.containers[0].preferences").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.containers[0].hasWebUi").value(true));
    }

    @Test
    void shouldReturnBooleanAvailabilityForEmptyAndAmbiguousContainers() throws Exception {
        var empty = createContainer("empty", Set.of());
        var ambiguous = createContainer("ambiguous");
        ambiguous.setUrlCandidates(List.of(
                new Endpoint("one", "traefik", "https://one.example/", false),
                new Endpoint("two", "traefik", "https://two.example/", false)));
        when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(true, false)))
                .thenReturn(inventory(List.of(empty, ambiguous)));
        mockMvc.perform(get("/api/containers").param("includeWithoutEndpoints", "true"))
                .andExpect(jsonPath("$.containers[0].hasWebUi").value(false))
                .andExpect(jsonPath("$.containers[0].hasEndpoints").value(false))
                .andExpect(jsonPath("$.containers[0].preferences").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.containers[1].hasWebUi").value(true))
                .andExpect(jsonPath("$.containers[1].hasEndpoints").value(true))
                .andExpect(jsonPath("$.containers[1].endpoints[*].selected")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))))
                .andExpect(jsonPath("$.containers[1].preferences").doesNotHaveJsonPath());
    }

    @Test
    void shouldAcceptCustomSelection() throws Exception {
        mockMvc.perform(put("/api/containers/selected-url")
                        .contentType("application/json")
                        .content("""
                                {"sourceId":"local","containerName":"app",
                                  "selectedEndpoint":{"id":"custom","source":"custom","url":"https://app.example"}}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(preferencesStore)
                .replaceSelection(
                        new ContainerPreferencesKey("local", "app"),
                        new SelectedEndpoint("custom", "custom", "https://app.example"));
        verifyNoInteractions(containerService, versions);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"id\":\"published-port:3000\",\"source\":\"published-port\"}",
                "{\"id\":\"none\",\"source\":\"none\"}",
                "null"
            })
    void shouldSaveDiscoveredNoneAndAutomaticSelections(String selection) throws Exception {
        mockMvc.perform(put("/api/containers/selected-url")
                        .contentType("application/json")
                        .content("{\"sourceId\":\"remote\",\"containerName\":\"app\",\"selectedEndpoint\":" + selection
                                + "}"))
                .andExpect(status().isNoContent());
        verify(preferencesStore)
                .replaceSelection(eq(new ContainerPreferencesKey("remote", "app")), nullable(SelectedEndpoint.class));
        verifyNoInteractions(containerService, versions);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "{",
                "{\"sourceId\":\"local\",\"containerName\":\"app\"}",
                "{\"sourceId\":\" \",\"containerName\":\"app\",\"selectedEndpoint\":null}",
                "{\"sourceId\":\"local\",\"containerName\":\" \",\"selectedEndpoint\":null}",
                "{\"sourceId\":\"unconfigured\",\"containerName\":\"app\",\"selectedEndpoint\":null}",
                "{\"containerName\":\"app\",\"selectedEndpoint\":null}",
                "{\"sourceId\":\"local\",\"selectedEndpoint\":null}",
                "{\"sourceId\":\"local\",\"containerName\":\"app\",\"selectedEndpoint\":{}}",
                "{\"sourceId\":\"local\",\"containerName\":\"app\",\"selectedEndpoint\":{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"javascript:alert(1)\"}}",
                "{\"sourceId\":\"local\",\"containerName\":\"app\",\"selectedEndpoint\":{\"id\":\"custom\",\"source\":\"custom\",\"url\":\"https://user:pass@app.example\"}}"
            })
    void shouldRejectMalformedUpdatesWithoutSaving(String body) throws Exception {
        mockMvc.perform(put("/api/containers/selected-url")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(preferencesStore, containerService, versions);
    }

    @Test
    void shouldReturnSafeErrorForFailedSave() throws Exception {
        when(preferencesStore.replaceSelection(any(), isNull()))
                .thenThrow(new IllegalStateException("Failed to save /private/secrets/preferences.json"));
        mockMvc.perform(put("/api/containers/selected-url")
                        .contentType("application/json")
                        .content("""
                                {"sourceId":"local","containerName":"app","selectedEndpoint":null}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(
                        content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/private"))));
    }

    @Test
    void shouldReturnResolvedCustomCandidateWithoutPreferences() throws Exception {
        var container = createContainer("app");
        resolveSelection(container, new SelectedEndpoint("custom", "custom", "https://app.example"));
        when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                .thenReturn(inventory(List.of(container)));
        mockMvc.perform(get("/api/containers"))
                .andExpect(jsonPath("$.containers[0].preferences").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.containers[0].hasWebUi").value(true))
                .andExpect(jsonPath("$.containers[0].endpoints.length()").value(2))
                .andExpect(jsonPath("$.containers[0].endpoints[0].selected").value(false))
                .andExpect(jsonPath("$.containers[0].endpoints[1].id").value("custom"))
                .andExpect(jsonPath("$.containers[0].endpoints[1].source").value("custom"))
                .andExpect(jsonPath("$.containers[0].endpoints[1].url").value("https://app.example"))
                .andExpect(jsonPath("$.containers[0].endpoints[1].selected").value(true))
                .andExpect(jsonPath("$.containers[0].hasEndpoints").value(true));
    }

    @Test
    void shouldKeepEndpointAvailabilitySeparateFromNoWebUiChoice() throws Exception {
        var container = createContainer("app");
        resolveSelection(container, new SelectedEndpoint("none", "none", null));
        when(containerService.getContainers(new com.roomelephant.porthole.core.model.ContainerQuery(false, false)))
                .thenReturn(inventory(List.of(container)));
        mockMvc.perform(get("/api/containers"))
                .andExpect(jsonPath("$.containers[0].preferences").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.containers[0].hasWebUi").value(false))
                .andExpect(jsonPath("$.containers[0].endpoints.length()").value(1))
                .andExpect(jsonPath("$.containers[0].endpoints[0].selected").value(false))
                .andExpect(jsonPath("$.containers[0].hasEndpoints").value(true));
    }

    private com.roomelephant.porthole.core.model.ContainerInventory inventory(List<Container> containers) {
        return new com.roomelephant.porthole.core.model.ContainerInventory(
                List.of(new com.roomelephant.porthole.core.model.SourceState(
                        "local", "Local", com.roomelephant.porthole.core.model.SourceStatus.UP, null)),
                containers);
    }

    private Container createContainer(String name) {
        return createContainer(name, Set.of(80));
    }

    private Container createContainer(String name, Set<Integer> ports) {
        Container container = new Container(
                name + "-id",
                "local",
                name,
                name,
                "nginx:latest",
                new Container.Details(ports, Map.of("secret", "internal-only"), "test-project", "running", "Up 1 hour"),
                ports.stream()
                        .map(port ->
                                new Endpoint("published-port:" + port, "published-port", "http://localhost/", false))
                        .toList());
        container.setIconUrl("https://example.com/nginx.png");
        resolveSelection(container, null);
        return container;
    }

    private void resolveSelection(Container container, SelectedEndpoint selection) {
        var store = new ContainerPreferencesStore() {
            @Override
            public Map<ContainerPreferencesKey, ContainerPreferences> snapshot() {
                return Map.of(
                        new ContainerPreferencesKey(container.sourceId(), container.name()),
                        new ContainerPreferences(selection));
            }

            @Override
            public ContainerPreferences replaceSelection(ContainerPreferencesKey key, SelectedEndpoint selected) {
                throw new AssertionError("Inventory must not write preferences");
            }
        };
        new ContainerPreferencesEnricher(store)
                .enrich(List.of(container), new EnrichmentContext("local", "localhost", false, false));
    }
}
