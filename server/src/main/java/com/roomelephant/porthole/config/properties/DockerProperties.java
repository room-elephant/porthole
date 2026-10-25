package com.roomelephant.porthole.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@NullMarked
@ConfigurationProperties(prefix = "porthole.docker", ignoreUnknownFields = false)
@Validated
@RegisterReflectionForBinding({DockerProperties.class, DockerConnectionProperties.class})
public record DockerProperties(@NotEmpty List<@Valid DockerConnectionProperties> connections) {
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
