package com.roomelephant.porthole.inbound.health;

import com.roomelephant.porthole.core.port.inbound.CheckDockerAvailability;
import java.util.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.health.contributor.*;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class DockerHealthIndicator implements HealthIndicator {
    private static final Map<String, String> UP_DETAILS = Map.of("status", "UP");
    private static final Map<String, String> DOWN_DETAILS =
            Map.of("status", "DOWN", "error", "Docker is not reachable or the health probe timed out");

    private final CheckDockerAvailability availability;

    public DockerHealthIndicator(CheckDockerAvailability availability) {
        this.availability = availability;
    }

    @Override
    public Health health() {
        var result = availability.checkAvailability();
        var details = new LinkedHashMap<String, Object>();
        result.sources().forEach((id, up) -> details.put(id, up ? UP_DETAILS : DOWN_DETAILS));
        return (result.anyAvailable() ? Health.up() : Health.down())
                .withDetail("sources", details)
                .build();
    }
}
