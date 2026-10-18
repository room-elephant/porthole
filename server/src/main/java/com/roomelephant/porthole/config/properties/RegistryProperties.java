package com.roomelephant.porthole.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@NullMarked
@ConfigurationProperties(prefix = "porthole.registry")
@Validated
@RegisterReflectionForBinding(RegistryProperties.class)
public record RegistryProperties(
        @Valid @NotNull(message = "Timeout configuration is required")
        Timeout timeout,

        @Valid @NotNull(message = "Cache configuration is required")
        Cache cache,

        @Valid @NotNull(message = "Docker Hub configuration is required")
        DockerHub dockerhub,

        @Valid @NotNull(message = "GHCR configuration is required")
        Ghcr ghcr) {
    /** Transport overrides do not change the semantic ghcr.io image identity. */
    public record Ghcr(
            @NotBlank String registryUrl, @NotBlank String authUrl) {}

    public record DockerHub(
            @NotBlank(message = "Registry URL must be configured")
            String registryUrl,

            @NotBlank(message = "Auth URL must be configured")
            String authUrl,

            @NotBlank(message = "Repositories URL must be configured")
            String repositoriesUrl) {}

    public record Timeout(
            @NotNull(message = "Connect timeout must be configured")
            @DurationMin(nanos = 1, message = "Connect timeout must be positive")
            Duration connect,

            @NotNull(message = "Read timeout must be configured")
            @DurationMin(nanos = 1, message = "Read timeout must be positive")
            Duration read) {}

    public record Cache(
            @NotNull(message = "Cache TTL must be configured")
            @DurationMin(nanos = 1, message = "Cache TTL must be positive")
            Duration ttl,

            @Positive(message = "Version cache size must be positive")
            int versionMaxSize) {}
}
