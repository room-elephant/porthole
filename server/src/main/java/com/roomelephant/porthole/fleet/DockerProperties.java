package com.roomelephant.porthole.fleet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.List;
import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@NullMarked
@ConfigurationProperties(prefix = "porthole.docker", ignoreUnknownFields = false)
@Validated
@RegisterReflectionForBinding({DockerProperties.class, DockerConnectionProperties.class})
public record DockerProperties(
        @NotEmpty List<@Valid DockerConnectionProperties> connections,

        @NotNull @DurationMin(nanos = 1, message = "Poll interval must be positive") @DefaultValue("30s")
        Duration pollInterval,

        @NotNull @DurationMin(nanos = 1, message = "Snapshot retention must be positive") @DefaultValue("24h")
        Duration snapshotRetention) {
    public DockerProperties {
        connections = connections == null ? List.of() : List.copyOf(connections);
    }

    @AssertTrue(message = "Docker source IDs must be unique")
    public boolean isIdsUnique() {
        return connections.stream()
                        .map(DockerConnectionProperties::id)
                        .distinct()
                        .count()
                == connections.size();
    }
}
