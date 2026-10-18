package com.roomelephant.porthole.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.model.SourceStatus;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.inbound.web.dto.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

class CompatibilityFixturesTest {
    @Test
    void unchangedIntegrationFixturesReadTheProductionWireContract() throws Exception {
        var json = new JacksonJsonHttpMessageConverter().getMapper();
        var container = new ContainerDTO(
                "id",
                "source",
                "name",
                "display",
                "nginx:1",
                List.of(new EndpointDTO("published-port:80", "published-port", "http://host:80/")),
                "docker.webp",
                null,
                "running",
                "Up",
                new VersionInfo("1", "2", true));
        var inventory = new ContainerInventoryDTO(
                List.of(
                        new SourceDTO("source", "Source", SourceStatus.UP, null),
                        new SourceDTO("failed", "Failed", SourceStatus.DOWN, "Docker is not reachable")),
                List.of(container));
        var fixture = json.readValue(
                json.writeValueAsString(inventory),
                com.roomelephant.porthole.controller.model.ContainerInventoryDTO.class);
        assertThat(fixture.sources().getFirst().status())
                .isEqualTo(com.roomelephant.porthole.domain.model.SourceStatus.UP);
        assertThat(fixture.sources().getFirst().error()).isNull();
        assertThat(fixture.sources().get(1).error()).isEqualTo("Docker is not reachable");
        assertThat(fixture.containers().getFirst().hasEndpoints()).isTrue();
        assertThat(fixture.containers().getFirst().project()).isNull();
        assertThat(fixture.containers().getFirst().version())
                .isEqualTo(new com.roomelephant.porthole.domain.model.VersionInfo("1", "2", true));
        assertThat(json.readTree(json.writeValueAsString(fixture)))
                .isEqualTo(json.readTree(json.writeValueAsString(inventory)));
    }

    @Test
    void unresolvedVersionsAndEmptyEndpointsKeepTheirWireShape() throws Exception {
        var json = new JacksonJsonHttpMessageConverter().getMapper();
        var container = new ContainerDTO(
                "id", "source", "name", "name", "nginx:1", List.of(), "docker.webp", null, null, null, null);
        var fixture = json.readValue(
                json.writeValueAsString(container), com.roomelephant.porthole.controller.model.ContainerDTO.class);
        assertThat(fixture.version()).isNull();
        assertThat(fixture.hasEndpoints()).isFalse();
        assertThat(fixture.endpoints()).isEmpty();
    }
}
