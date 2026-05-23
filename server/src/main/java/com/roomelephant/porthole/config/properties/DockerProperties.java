package com.roomelephant.porthole.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "porthole.docker")
@Validated
@RegisterReflectionForBinding(DockerProperties.class)
public record DockerProperties(
        @NotBlank(message = "Docker host must be configured")
        String host,

        @NotNull(message = "Connection timeout must be configured")
        Duration connectionTimeout,

        @NotNull(message = "Response timeout must be configured")
        Duration responseTimeout) {}
