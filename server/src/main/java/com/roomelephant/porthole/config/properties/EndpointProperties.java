package com.roomelephant.porthole.config.properties;

import com.roomelephant.porthole.core.enrichment.endpoint.EndpointUrls;
import jakarta.validation.constraints.AssertTrue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@NullMarked
@ConfigurationProperties(prefix = "porthole.endpoints")
@Validated
@RegisterReflectionForBinding(EndpointProperties.class)
public record EndpointProperties(@Nullable String publishedHost) {
    @AssertTrue(message = "Published host must be a hostname or IP address without scheme, port, or path")
    public boolean isPublishedHostValid() {
        return publishedHost == null || EndpointUrls.isValidHost(publishedHost);
    }
}
