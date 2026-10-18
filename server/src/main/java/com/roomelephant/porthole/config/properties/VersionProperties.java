package com.roomelephant.porthole.config.properties;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@NullMarked
@ConfigurationProperties(prefix = "porthole.versions")
@RegisterReflectionForBinding(VersionProperties.class)
public record VersionProperties(
        @NotNull @DurationMin(nanos = 1, message = "Version refresh interval must be positive")
        Duration refreshInterval) {
    private static final Duration MAX_REFRESH_INTERVAL = Duration.ofNanos(Long.MAX_VALUE);

    public VersionProperties {
        if (refreshInterval != null && refreshInterval.compareTo(MAX_REFRESH_INTERVAL) > 0) {
            throw new IllegalArgumentException("Version refresh interval must fit in nanoseconds");
        }
    }
}
