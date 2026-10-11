package com.roomelephant.porthole.controller.model;

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
                "my-container",
                "my-container",
                "nginx:latest",
                List.of(new EndpointDTO("a", "traefik", "https://example.com/")),
                "https://example.com/nginx.png",
                "my-project",
                "running",
                "Up 2 hours");

        assertTrue(dto.hasEndpoints());
    }

    @Test
    @DisplayName("should return false for hasEndpoints when ports list is empty")
    void shouldReturnFalseForHasEndpointsWhenPortsListIsEmpty() {
        ContainerDTO dto = new ContainerDTO(
                "container-id",
                "my-container",
                "my-container",
                "nginx:latest",
                Collections.emptyList(),
                "https://example.com/nginx.png",
                "my-project",
                "running",
                "Up 2 hours");

        assertFalse(dto.hasEndpoints());
    }

    @Test
    @DisplayName("should correctly expose all record components")
    void shouldCorrectlyExposeAllRecordComponents() {
        ContainerDTO dto = new ContainerDTO(
                "abc123",
                "test-container",
                "test",
                "redis:7",
                List.of(new EndpointDTO("published-port:6379", "published-port", "http://localhost:6379/")),
                "https://example.com/redis.png",
                "test-project",
                "running",
                "Up 1 hour");

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
    }
}
