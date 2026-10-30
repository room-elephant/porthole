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
                List.of(new EndpointDTO("a", "traefik", "https://example.com/", false)),
                "https://example.com/nginx.png",
                "my-project",
                "running",
                "Up 2 hours",
                null,
                false,
                true);

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
                true);

        assertFalse(dto.hasEndpoints());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldKeepCandidatePresenceIndependentOfWebUiAvailability(boolean hasWebUi) {
        var dto = new ContainerDTO(
                "id", "local", "app", "app", "nginx", List.of(), "", null, "running", "Up", null, false, hasWebUi);
        assertFalse(dto.hasEndpoints());
        assertEquals(hasWebUi, dto.hasWebUi());
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
                List.of(new EndpointDTO("published-port:6379", "published-port", "http://localhost:6379/", false)),
                "https://example.com/redis.png",
                "test-project",
                "running",
                "Up 1 hour",
                null,
                true,
                true);

        assertEquals("abc123", dto.id());
        assertEquals("test-container", dto.name());
        assertEquals("test", dto.displayName());
        assertEquals("redis:7", dto.image());
        assertEquals(
                List.of(new EndpointDTO("published-port:6379", "published-port", "http://localhost:6379/", false)),
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
                true);

        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(dto);

        assertTrue(json.contains("\"self\":true"), json);
    }
}
