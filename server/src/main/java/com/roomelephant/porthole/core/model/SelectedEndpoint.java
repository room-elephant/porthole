package com.roomelephant.porthole.core.model;

import java.net.URI;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record SelectedEndpoint(
        String id, String source, @Nullable String url) {
    public boolean hasValidCustomUrl() {
        if (!"custom".equals(id) || !"custom".equals(source) || url == null) {
            return false;
        }
        try {
            var uri = URI.create(url);
            return uri.getHost() != null
                    && uri.getUserInfo() == null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException _) {
            return false;
        }
    }
}
