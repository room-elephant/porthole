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
        assertThat(response.getBody()).contains("{\"status\":\"UP\"}");
    }

    @Test
    void shouldReturnUpWhenDockerIsReachable() {
        ResponseEntity<String> response = fetch("/actuator/health");

        assertThat(response.getStatusCode().value()).isEqualTo(OK.value());
        assertThat(response.getBody()).contains("\"status\":\"UP\"}");
        assertThat(response.getBody()).contains("\"docker\":{\"status\":\"UP\"}");
    }
}
