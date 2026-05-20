package com.roomelephant.porthole.it.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

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

}
