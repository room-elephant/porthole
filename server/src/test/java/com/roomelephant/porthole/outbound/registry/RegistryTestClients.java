package com.roomelephant.porthole.outbound.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomelephant.porthole.config.RegistryConfig;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import java.util.List;
import org.springframework.web.client.RestClient;

final class RegistryTestClients {
    private RegistryTestClients() {}

    static RegistryOperationsAdapter operations(RestClient http, RegistryProperties properties) {
        var config = new RegistryConfig();
        var json = new ObjectMapper();
        return new RegistryOperationsAdapter(
                List.of(config.dockerHubClient(http, json, properties), config.ghcrClient(http, json, properties)),
                properties.cache());
    }
}
