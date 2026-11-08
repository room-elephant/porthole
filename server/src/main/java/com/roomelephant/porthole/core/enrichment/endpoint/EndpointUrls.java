package com.roomelephant.porthole.core.enrichment.endpoint;

import com.roomelephant.porthole.shared.Hosts;
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
        return Hosts.isValidHost(host);
    }

    private static String build(String scheme, String host, int port, String path) {
        try {
            return new URI(scheme, null, host.toLowerCase(Locale.ROOT), port, path, null, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid endpoint destination", e);
        }
    }
}
