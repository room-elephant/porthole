package com.roomelephant.porthole.controller;

import com.roomelephant.porthole.config.properties.EndpointProperties;
import com.roomelephant.porthole.domain.model.EndpointContext;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class EndpointContextResolver {
    private final EndpointProperties properties;

    public EndpointContextResolver(EndpointProperties properties) {
        this.properties = properties;
    }

    public EndpointContext resolve(HttpServletRequest request) {
        var override = properties.publishedHost();
        return new EndpointContext(override == null ? request.getServerName() : override);
    }
}
