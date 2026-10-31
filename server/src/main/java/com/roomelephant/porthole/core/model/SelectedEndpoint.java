package com.roomelephant.porthole.core.model;

import java.net.URI;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record SelectedEndpoint(
        String id, String source, @Nullable String url) {
    /** Reserved id and source of the explicit "no web UI" selection. */
    public static final String NONE = "none";

    /** Reserved id and source of a user-supplied URL selection. */
    public static final String CUSTOM = "custom";

    public static final int MAX_URL_LENGTH = 2048;

    public boolean selectsNone() {
        return NONE.equals(id);
    }

    public boolean selectsCustom() {
        return CUSTOM.equals(source);
    }

    public boolean hasValidCustomUrl() {
        if (!CUSTOM.equals(id) || !CUSTOM.equals(source) || url == null || url.length() > MAX_URL_LENGTH) {
            return false;
        }
        try {
            var uri = URI.create(url);
            var authority = uri.getRawAuthority();
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && authority != null
                    && !authority.contains("@")
                    && !hostOf(uri, authority).isBlank();
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    /**
     * URI only populates the host for RFC 2396 server-based authorities, so hostnames browsers accept (underscores,
     * non-ASCII) fall back to the registry-based authority with any trailing port removed.
     */
    private static String hostOf(URI uri, String authority) {
        var host = uri.getHost();
        return host != null ? host : authority.replaceFirst(":\\d*$", "");
    }
}
