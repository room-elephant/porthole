package com.roomelephant.porthole.it.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.roomelephant.porthole.it.infra.ContainerAwareIntegrationTestBase;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SourcesEndpointIT extends ContainerAwareIntegrationTestBase {

    @Test
    void localSourceIsUpWithLastSuccessAt() {
        JsonNode sources = new JsonMapper().readTree(fetch("/api/sources").getBody());

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).path("id").asText()).isEqualTo("local");
        assertThat(sources.get(0).path("status").asText()).isEqualTo("UP");
        assertThat(sources.get(0).path("lastSuccessAt").asText()).isNotBlank();
    }

    @Test
    void newContainerAppearsWithoutRestart() {
        startPorthole();
        String name = "porthole-test-late-" + UUID.randomUUID();
        try (GenericContainer<?> late = new GenericContainer<>(PAUSE_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(name))
                .withExposedPorts(8080)) {
            late.start();
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> dashboard().containers().stream()
                            .anyMatch(container -> container.name().equals(name)));
        }
    }
}
