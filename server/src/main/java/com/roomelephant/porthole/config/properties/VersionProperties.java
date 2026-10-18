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
    public VersionProperties {
        // Scheduling requires a duration representable in nanoseconds.
        if (refreshInterval != null) refreshInterval.toNanos();
    }
}
