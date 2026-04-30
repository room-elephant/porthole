package com.roomelephant.porthole.it.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import com.roomelephant.porthole.it.infra.IntegrationTestBase;
import com.roomelephant.porthole.it.infra.RunWithoutContainers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.ResponseEntity;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(2)
class DockerHealthIT extends IntegrationTestBase {

    @Test
    @Order(1)
    @RunWithoutContainers
    void shouldReturnUpWhenDockerIsReachableAndRequestOnlyDockerComponent() {
        ResponseEntity<String> response = fetch("/actuator/health/docker");

        assertThat(response.getStatusCode().value()).isEqualTo(OK.value());
        assertThat(response.getBody()).contains("{\"status\":\"UP\"}");
    }

    @Test
    @Order(2)
    @RunWithoutContainers
    void shouldReturnUpWhenDockerIsReachable() {
        ResponseEntity<String> response = fetch("/actuator/health");

        assertThat(response.getStatusCode().value()).isEqualTo(OK.value());
        assertThat(response.getBody()).contains("\"status\":\"UP\"}");
        assertThat(response.getBody()).contains("\"docker\":{\"status\":\"UP\"}");
    }

    @Test
    @Order(999)
    @RunWithoutContainers
    void shouldReturnDownWhenDockerIsUnreachable() {
        pauseDocker();

        try {
            ResponseEntity<String> response = fetch("/actuator/health");

            assertThat(response.getStatusCode().value()).isEqualTo(SERVICE_UNAVAILABLE.value());
            assertThat(response.getBody()).contains("\"status\":\"DOWN\"");
            assertThat(response.getBody())
                    .contains(
                            "\"docker\":{\"details\":{\"Unexpected exception\":\"java.net.SocketTimeoutException: Read timed out\"},\"status\":\"DOWN\"}");
        } finally {
            unpauseDocker();
        }
    }
}
