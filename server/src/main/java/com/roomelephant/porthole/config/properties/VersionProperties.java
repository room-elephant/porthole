package com.roomelephant.porthole.config.properties;

import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;

@NullMarked
@ConfigurationProperties(prefix = "porthole.versions")
@RegisterReflectionForBinding(VersionProperties.class)
public record VersionProperties(Duration refreshInterval) {
    public VersionProperties {
        if (refreshInterval == null || refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalArgumentException("Version refresh interval must be positive");
        }
        // Scheduling requires a duration representable in nanoseconds.
        refreshInterval = Duration.ofNanos(refreshInterval.toNanos());
    }
}
