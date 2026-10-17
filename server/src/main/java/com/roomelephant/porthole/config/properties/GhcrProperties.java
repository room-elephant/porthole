package com.roomelephant.porthole.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Transport overrides do not change the semantic ghcr.io image identity. */
@ConfigurationProperties(prefix = "registry.ghcr")
@Validated
@RegisterReflectionForBinding(GhcrProperties.class)
public record GhcrProperties(
        @NotBlank String registryUrl, @NotBlank String authUrl) {}
