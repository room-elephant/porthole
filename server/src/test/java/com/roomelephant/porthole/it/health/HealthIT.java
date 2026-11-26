package com.roomelephant.porthole.it.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.it.infra.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class HealthIT extends IntegrationTestBase {

    @Test
    void healthIsUpWithoutDockerComponent() throws Exception {
        ResponseEntity<String> response = fetch("/actuator/health");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = new tools.jackson.databind.json.JsonMapper().readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.path("components").has("docker")).isFalse();
        assertThat(fetch("/actuator/health/liveness").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void dockerHealthEndpointIsGone() {
        assertThat(fetch("/actuator/health/docker").getStatusCode().value()).isEqualTo(404);
    }
}
