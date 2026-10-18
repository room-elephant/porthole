package com.roomelephant.porthole.config.properties;

import com.roomelephant.porthole.core.enrichment.endpoint.EndpointUrls;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.*;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@NullMarked
@RegisterReflectionForBinding(DockerConnectionProperties.class)
public record DockerConnectionProperties(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_-]*") String id,
        @NotBlank String name,
        @NotBlank String host,
        @Nullable String publishedHost,
        @NotNull @DefaultValue("5s") Duration connectionTimeout,
        @NotNull @DefaultValue("60s") Duration responseTimeout) {
    @AssertTrue(
            message =
                    "Docker host must be an absolute unix:/// socket or tcp://host:port URI; provision SSH externally")
    public boolean isHostValid() {
        if (host == null) return false;
        try {
            URI uri = URI.create(host);
            if (uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) return false;
            return switch (String.valueOf(uri.getScheme())) {
                case "unix" ->
                    uri.getAuthority() == null
                            && uri.getPath() != null
                            && uri.getPath().startsWith("/")
                            && uri.getPath().length() > 1;
                case "tcp" ->
                    uri.getHost() != null
                            && uri.getPort() > 0
                            && uri.getPort() <= 65535
                            && (uri.getPath() == null || uri.getPath().isEmpty());
                default -> false;
            };
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    @AssertTrue(message = "Docker timeouts must be positive")
    public boolean isTimeoutsValid() {
        return connectionTimeout != null
                && responseTimeout != null
                && connectionTimeout.isPositive()
                && responseTimeout.isPositive();
    }

    @AssertTrue(message = "Published host must be a bare hostname or IP address")
    public boolean isPublishedHostValid() {
        return publishedHost == null || EndpointUrls.isValidHost(publishedHost);
    }
}
