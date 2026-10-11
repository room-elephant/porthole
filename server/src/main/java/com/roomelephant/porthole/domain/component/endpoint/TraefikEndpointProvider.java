package com.roomelephant.porthole.domain.component.endpoint;

import com.roomelephant.porthole.domain.model.ContainerSnapshot;
import com.roomelephant.porthole.domain.model.EndpointContext;
import com.roomelephant.porthole.domain.model.EndpointDTO;
import com.roomelephant.porthole.domain.util.EndpointUrls;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
@Slf4j
public class TraefikEndpointProvider implements EndpointProvider {
    private static final String ROUTER_PREFIX = "traefik.http.routers.";
    private final TraefikRuleParser parser;

    public TraefikEndpointProvider(TraefikRuleParser parser) {
        this.parser = parser;
    }

    @Override
    public List<EndpointDTO> discover(ContainerSnapshot container, EndpointContext context) {
        var labels = new TreeMap<String, String>();
        container.labels().forEach((key, value) -> labels.put(key.toLowerCase(Locale.ROOT), value));
        if (labels.getOrDefault("traefik.enable", "").strip().equalsIgnoreCase("false")) return List.of();
        var endpoints = new ArrayList<EndpointDTO>();
        labels.forEach((key, rule) -> {
            if (!key.startsWith(ROUTER_PREFIX)
                    || !key.endsWith(".rule")
                    || key.length() <= ROUTER_PREFIX.length() + ".rule".length()) return;
            String router = key.substring(0, key.length() - ".rule".length());
            var route = parser.parse(rule);
            if (route.isEmpty()) {
                log.debug("Skipping unsupported router {} on container {}", router, container.name());
                return;
            }
            String url = EndpointUrls.routeUrl(
                    scheme(labels, router), route.get().host(), route.get().path());
            endpoints.add(new EndpointDTO("traefik:" + url, "traefik", url));
        });
        return endpoints.stream()
                .distinct()
                .sorted(Comparator.comparing(EndpointDTO::url))
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
