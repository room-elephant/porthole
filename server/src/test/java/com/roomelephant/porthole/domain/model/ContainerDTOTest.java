package com.roomelephant.porthole.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContainerDTO")
class ContainerDTOTest {

    @Test
    @DisplayName("should return true for hasPublicPorts when ports are present")
    void shouldReturnTrueForHasPublicPortsWhenPortsArePresent() {
        var dto = new ContainerDTO(
                "container-id",
                "my-container",
                "my-container",
                "nginx:latest",
                Set.of(80, 443),
                "https://example.com/nginx.png",
                "my-project",
                "running",
                "Up 2 hours");

        assertTrue(dto.hasPublicPorts());
    }

    @Test
    @DisplayName("should return false for hasPublicPorts when ports list is empty")
    void shouldReturnFalseForHasPublicPortsWhenPortsListIsEmpty() {
        var dto = new ContainerDTO(
                "container-id",
                "my-container",
                "my-container",
                "nginx:latest",
                Collections.emptySet(),
                "https://example.com/nginx.png",
                "my-project",
                "running",
                "Up 2 hours");

        assertFalse(dto.hasPublicPorts());
    }

    @Test
    @DisplayName("should return false for hasPublicPorts when ports list is null")
    void shouldReturnFalseForHasPublicPortsWhenPortsListIsNull() {
        var dto = new ContainerDTO(
                "container-id",
                "my-container",
                "my-container",
                "nginx:latest",
                null,
                "https://example.com/nginx.png",
                "my-project",
                "running",
                "Up 2 hours");

        assertFalse(dto.hasPublicPorts());
    }

    @Test
    @DisplayName("should correctly expose all record components")
    void shouldCorrectlyExposeAllRecordComponents() {
        var dto = new ContainerDTO(
                "abc123",
                "test-container",
                "test",
                "redis:7",
                Set.of(6379),
                "https://example.com/redis.png",
                "test-project",
                "running",
                "Up 1 hour");

        assertEquals("abc123", dto.id());
        assertEquals("test-container", dto.name());
        assertEquals("test", dto.displayName());
        assertEquals("redis:7", dto.image());
        assertEquals(Set.of(6379), dto.exposedPorts());
        assertEquals("https://example.com/redis.png", dto.iconUrl());
        assertEquals("test-project", dto.project());
        assertEquals("running", dto.state());
        assertEquals("Up 1 hour", dto.status());
    }
}
