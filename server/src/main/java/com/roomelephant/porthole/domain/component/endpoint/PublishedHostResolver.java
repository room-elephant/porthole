package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.config.properties.EndpointProperties;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class PublishedHostResolver {
    private final EndpointProperties properties;

    public PublishedHostResolver(EndpointProperties properties) {
        this.properties = properties;
    }

    public String resolve(com.roomelephant.porthole.domain.model.ContainerContext context) {
        return context.publishedHost() == null ? resolve(context.host()) : context.publishedHost();
    }

    public String resolve(String requestHostname) {
        String override = properties.publishedHost();
        return override == null ? requestHostname : override;
    }
}
