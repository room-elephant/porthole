package com.roomelephant.porthole.domain.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class EndpointUrls {
    private EndpointUrls() {}

    public static String publishedPortUrl(String host, int port) {
        return build("http", host, port == 80 ? -1 : port, "/");
    }

    public static String routeUrl(String scheme, String host, String path) {
        return build(scheme, host, -1, path.isEmpty() ? "/" : path);
    }

    public static boolean isValidHost(String host) {
        if (host.isEmpty() || !host.equals(host.strip())) return false;
        try {
            URI uri = new URI("http", null, host, -1, "/", null, null);
            String expectedHost = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
            return uri.getHost() != null && uri.getHost().equalsIgnoreCase(expectedHost);
        } catch (URISyntaxException | IllegalArgumentException _) {
            return false;
        }
    }

    private static String build(String scheme, String host, int port, String path) {
        try {
            return new URI(scheme, null, host.toLowerCase(Locale.ROOT), port, path, null, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid endpoint destination", e);
        }
    }
}
