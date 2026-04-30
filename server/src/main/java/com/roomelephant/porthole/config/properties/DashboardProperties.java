package com.roomelephant.porthole.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "dashboard")
@Validated
public record DashboardProperties(
        @Valid @NotNull(message = "Icons configuration is required")
        Icons icons) {
    public record Icons(
            @NotBlank(message = "Icons path must be configured")
            String path,

            @NotBlank(message = "Icons URL must be configured")
            String url,

            @NotBlank(message = "Icons extension must be configured")
            String extension) {}
}
