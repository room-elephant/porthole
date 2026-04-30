package com.roomelephant.porthole.config;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DockerHealthIndicator implements HealthIndicator {

    private final DockerClient dockerClient;

    public DockerHealthIndicator(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public Health health() {
        try {
            dockerClient.pingCmd().exec();
            return Health.up().build();
        } catch (RuntimeException e) {
            if (isDockerConnectionError(e)) {
                String errorMessage = e.getCause().getMessage();
                log.error("Error connecting to docker: {}", errorMessage);
                return Health.down()
                        .withDetail("Error connecting to docker", errorMessage)
                        .build();
            }
            return Health.down()
                    .withDetail("Unexpected exception", e.getMessage())
                    .build();
        }
    }

    private boolean isDockerConnectionError(RuntimeException e) {
        return e.getCause() instanceof java.net.SocketException;
    }
}
