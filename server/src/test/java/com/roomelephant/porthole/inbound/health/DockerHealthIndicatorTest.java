package com.roomelephant.porthole.inbound.health;

import static org.assertj.core.api.Assertions.*;

import com.roomelephant.porthole.core.model.DockerAvailability;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DockerHealthIndicatorTest {
    @Test
    void oneAvailableSourceKeepsOverallUpWithPerSourceDetails() {
        var health = new DockerHealthIndicator(() -> new DockerAvailability(Map.of("a", true, "b", false)));
        var result = health.health();
        assertThat(result.getStatus()).hasToString("UP");
        assertThat(result.getDetails())
                .containsEntry(
                        "sources",
                        Map.of(
                                "a", Map.of("status", "UP"),
                                "b",
                                        Map.of(
                                                "status",
                                                "DOWN",
                                                "error",
                                                "Docker is not reachable or the health probe timed out")));
    }

    @Test
    void allUnavailableSourcesAreDown() {
        var health = new DockerHealthIndicator(() -> new DockerAvailability(Map.of("a", false)));
        assertThat(health.health().getStatus()).hasToString("DOWN");
    }

    @Test
    void noSourcesAreDown() {
        var health = new DockerHealthIndicator(() -> new DockerAvailability(Map.of()));
        assertThat(health.health().getStatus()).hasToString("DOWN");
    }
}
