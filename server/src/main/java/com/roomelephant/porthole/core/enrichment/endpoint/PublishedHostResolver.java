package com.roomelephant.porthole.core.enrichment.endpoint;

import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class PublishedHostResolver {
    private final EndpointSettings properties;

    public PublishedHostResolver(EndpointSettings properties) {
        this.properties = properties;
    }

    public String resolve(com.roomelephant.porthole.core.enrichment.EnrichmentContext context) {
        String publishedHost = context.publishedHost();
        return publishedHost == null ? resolve(context.host()) : publishedHost;
    }

    public String resolve(String requestHostname) {
        String override = properties.publishedHost();
        return override == null ? requestHostname : override;
    }
}
