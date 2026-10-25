package com.roomelephant.porthole.core.enrichment.endpoint;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class PublishedHostResolver {
    public String resolve(EnrichmentContext context) {
        String publishedHost = context.publishedHost();
        return publishedHost == null ? "127.0.0.1" : publishedHost;
    }
}
