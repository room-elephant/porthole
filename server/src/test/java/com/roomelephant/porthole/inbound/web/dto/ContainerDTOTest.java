package com.roomelephant.porthole.inbound.web.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContainerDTO")
class ContainerDTOTest {

    @Test
    @DisplayName("should return true for hasEndpoints when ports are present")
    void shouldReturnTrueForHasEndpointsWhenPortsArePresent() {
        ContainerDTO dto = new ContainerDTO(
                "container-id",
                "local",
                "my-container",
                "my-container",
                "nginx:latest",
                List.of(new EndpointDTO("a", "traefik", "https://example.com/")),
                "https://example.com/nginx.png",
                "my-project",
                "running",
                "Up 2 hours",
                null,
                false,
                new ContainerPreferencesDTO(null));

        assertTrue(dto.hasEndpoints());
    }

    @Test
    @DisplayName("should return false for hasEndpoints when ports list is empty")
    void shouldReturnFalseForHasEndpointsWhenPortsListIsEmpty() {
        ContainerDTO dto = new ContainerDTO(
                "container-id",
                "local",
                "my-container",
                "my-container",
                "nginx:latest",
                Collections.emptyList(),
                "https://example.com/nginx.png",
                "my-project",
                "running",
                "Up 2 hours",
                null,
                false,
                new ContainerPreferencesDTO(null));

        assertFalse(dto.hasEndpoints());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("selectionsWithoutAvailableEndpoint")
    void shouldNotCountUnavailableSelectionsAsEndpoints(SelectedEndpointDTO selection) {
        var dto = new ContainerDTO(
                "app-id",
                "local",
                "app",
                "app",
                "nginx",
                List.of(),
                "",
                null,
                "running",
                "Up",
                null,
                false,
                new ContainerPreferencesDTO(selection));
        assertFalse(dto.hasEndpoints());
    }

    static java.util.stream.Stream<SelectedEndpointDTO> selectionsWithoutAvailableEndpoint() {
        return java.util.stream.Stream.of(
                null,
                new SelectedEndpointDTO("none", "none", null),
                new SelectedEndpointDTO("missing", "traefik", null),
                new SelectedEndpointDTO("custom", "custom", "javascript:alert(1)"));
    }

    @Test
    @DisplayName("should correctly expose all record components")
    void shouldCorrectlyExposeAllRecordComponents() {
        ContainerDTO dto = new ContainerDTO(
                "abc123",
                "local",
                "test-container",
                "test",
                "redis:7",
                List.of(new EndpointDTO("published-port:6379", "published-port", "http://localhost:6379/")),
                "https://example.com/redis.png",
                "test-project",
                "running",
                "Up 1 hour",
                null,
                true,
                new ContainerPreferencesDTO(null));

        assertEquals("abc123", dto.id());
        assertEquals("test-container", dto.name());
        assertEquals("test", dto.displayName());
        assertEquals("redis:7", dto.image());
        assertEquals(
                List.of(new EndpointDTO("published-port:6379", "published-port", "http://localhost:6379/")),
                dto.endpoints());
        assertEquals("https://example.com/redis.png", dto.iconUrl());
        assertEquals("test-project", dto.project());
        assertEquals("running", dto.state());
        assertEquals("Up 1 hour", dto.status());
        assertTrue(dto.self());
    }

    @Test
    @DisplayName("should serialise the self flag")
    void shouldSerialiseSelfFlag() throws Exception {
        ContainerDTO dto = new ContainerDTO(
                "abc123",
                "local",
                "porthole",
                "porthole",
                "porthole:latest",
                List.of(),
                "https://example.com/porthole.png",
                null,
                "running",
                "Up",
                null,
                true,
                new ContainerPreferencesDTO(null));

        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(dto);

        assertTrue(json.contains("\"self\":true"), json);
    }
}
