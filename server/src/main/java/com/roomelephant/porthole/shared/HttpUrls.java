package com.roomelephant.porthole.shared;

import java.net.URI;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The one rule for a URL a user may save: absolute http or https, with a host and no credentials. */
@NullMarked
public final class HttpUrls {
    public static final int MAX_LENGTH = 2048;

    private HttpUrls() {}

    public static boolean isValid(@Nullable String url) {
        if (url == null || url.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            @Nullable String authority = uri.getRawAuthority();
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && authority != null
                    && !authority.contains("@")
                    && !hostOf(uri, authority).isBlank();
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    private static String hostOf(URI uri, String authority) {
        @Nullable String host = uri.getHost();
        return host != null ? host : authority.replaceFirst(":\\d*$", "");
    }
}
