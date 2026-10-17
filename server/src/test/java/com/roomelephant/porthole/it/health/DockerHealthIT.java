package com.roomelephant.porthole.it.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import com.roomelephant.porthole.it.infra.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class DockerHealthIT extends IntegrationTestBase {

    @Test
    void shouldReturnUpWhenDockerIsReachableAndRequestOnlyDockerComponent() {
        ResponseEntity<String> response = fetch("/actuator/health/docker");

        assertThat(response.getStatusCode().value()).isEqualTo(OK.value());
        assertThat(response.getBody()).contains("\"status\":\"UP\"", "\"sources\"");
    }

    @Test
    void shouldReturnUpWhenDockerIsReachable() throws Exception {
        ResponseEntity<String> response = fetch("/actuator/health");

        assertThat(response.getStatusCode().value()).isEqualTo(OK.value());
        assertThat(response.getBody()).contains("\"status\":\"UP\"}");
        var body = new tools.jackson.databind.json.JsonMapper().readTree(response.getBody());
        assertThat(body.path("components").path("docker").path("status").asText())
                .isEqualTo("UP");
        assertThat(body.path("components")
                        .path("docker")
                        .path("details")
                        .path("sources")
                        .path("local")
                        .path("status")
                        .asText())
                .isEqualTo("UP");
    }
}
