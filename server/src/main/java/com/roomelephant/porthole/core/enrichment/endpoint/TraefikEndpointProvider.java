package com.roomelephant.porthole.core.enrichment.endpoint;

import com.roomelephant.porthole.core.enrichment.EnrichmentContext;
import com.roomelephant.porthole.core.enrichment.endpoint.TraefikRuleParser.Route;
import com.roomelephant.porthole.core.model.Container;
import com.roomelephant.porthole.core.model.Endpoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
@Slf4j
public final class TraefikEndpointProvider implements EndpointProvider {
    private static final String RULE_SUFFIX = ".rule";
    private static final String ROUTER_PREFIX = "traefik.http.routers.";
    private final TraefikRuleParser parser;

    public TraefikEndpointProvider(TraefikRuleParser parser) {
        this.parser = parser;
    }

    @Override
    public List<Endpoint> discover(Container container, EnrichmentContext context) {
        TreeMap<String, String> labels = new TreeMap<String, String>();
        container.labels().forEach((key, value) -> labels.put(key.toLowerCase(Locale.ROOT), value));
        if (labels.getOrDefault("traefik.enable", "").strip().equalsIgnoreCase("false")) return List.of();
        ArrayList<Endpoint> endpoints = new ArrayList<Endpoint>();
        labels.forEach((key, rule) -> {
            if (!key.startsWith(ROUTER_PREFIX)
                    || !key.endsWith(RULE_SUFFIX)
                    || key.length() <= ROUTER_PREFIX.length() + RULE_SUFFIX.length()) return;
            String router = key.substring(0, key.length() - RULE_SUFFIX.length());
            Optional<Route> route = parser.parse(rule);
            if (route.isEmpty()) {
                log.debug("Skipping unsupported router {} on container {}", router, container.name());
                return;
            }
            String url = EndpointUrls.routeUrl(
                    scheme(labels, router), route.get().host(), route.get().path());
            endpoints.add(new Endpoint("traefik:" + url, "traefik", url));
        });
        return endpoints.stream()
                .distinct()
                .sorted(Comparator.comparing(Endpoint::url))
                .toList();
    }

    private String scheme(Map<String, String> labels, String router) {
        String tls = labels.getOrDefault(router + ".tls", "").strip();
        if (tls.equalsIgnoreCase("false")) return "http";
        return tls.equalsIgnoreCase("true")
                        || labels.keySet().stream().anyMatch(key -> key.startsWith(router + ".tls."))
                ? "https"
                : "http";
    }
}
