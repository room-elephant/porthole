package com.roomelephant.porthole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import java.net.SocketException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;

@ExtendWith(MockitoExtension.class)
@DisplayName("DockerHealthIndicator")
class DockerHealthIndicatorTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private PingCmd pingCmd;

    private DockerHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new DockerHealthIndicator(dockerClient);
    }

    @Test
    @DisplayName("should return UP status when Docker is reachable")
    void shouldReturnUpStatusWhenDockerIsReachable() {
        when(dockerClient.pingCmd()).thenReturn(pingCmd);
        doNothing().when(pingCmd).exec();

        Health health = healthIndicator.health();

        assertEquals("UP", health.getStatus().toString());
    }

    @Test
    @DisplayName("should return DOWN status with error details when Docker is not reachable")
    void shouldReturnDownStatusWithErrorDetailsWhenDockerIsNotReachable() {
        when(dockerClient.pingCmd()).thenReturn(pingCmd);
        doThrow(new RuntimeException(new SocketException("Connection refused")))
                .when(pingCmd)
                .exec();

        Health health = healthIndicator.health();

        assertEquals("DOWN", health.getStatus().toString());
        assertEquals(
                "Error connecting to docker",
                health.getDetails().keySet().stream().findFirst().get());
        assertEquals("Connection refused", health.getDetails().get("Error connecting to docker"));
    }

    @Test
    @DisplayName("should return DOWN status with error details when Unexpected Error")
    void shouldReturnDownStatusWithErrorDetailsWhenUnexpectedError() {
        when(dockerClient.pingCmd()).thenReturn(pingCmd);
        doThrow(new RuntimeException("Connection refused")).when(pingCmd).exec();

        Health health = healthIndicator.health();

        assertEquals("DOWN", health.getStatus().toString());
        assertTrue(health.getDetails().containsKey("Unexpected exception"));
        assertEquals("Connection refused", health.getDetails().get("Unexpected exception"));
    }
}
