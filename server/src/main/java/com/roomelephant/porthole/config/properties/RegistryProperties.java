package com.roomelephant.porthole.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "registry")
@Validated
@RegisterReflectionForBinding(RegistryProperties.class)
public record RegistryProperties(
        @Valid @NotNull(message = "Timeout configuration is required")
        Timeout timeout,

        @Valid @NotNull(message = "Cache configuration is required")
        Cache cache,

        @Valid @NotNull(message = "Docker Hub configuration is required")
        DockerHub dockerhub) {
    public record DockerHub(
            @NotNull(message = "Registry URL must be configured")
            String registryUrl,

            @NotNull(message = "Auth URL must be configured")
            String authUrl,

            @NotNull(message = "Repositories URL must be configured")
            String repositoriesUrl) {}

    public record Timeout(
            @NotNull(message = "Connect timeout must be configured")
            Duration connect,

            @NotNull(message = "Read timeout must be configured")
            Duration read) {}

    public record Cache(
            @NotNull(message = "Cache TTL must be configured")
            Duration ttl,

            @Positive(message = "Version cache size must be positive")
            int versionMaxSize) {}
}
