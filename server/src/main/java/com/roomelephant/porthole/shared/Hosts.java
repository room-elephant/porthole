package com.roomelephant.porthole.shared;

import java.net.URI;
import java.net.URISyntaxException;
import org.jspecify.annotations.NullMarked;

/** The rule for a bare hostname or IP address, without scheme, port, path or credentials. */
@NullMarked
public final class Hosts {
    private Hosts() {}

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
}
