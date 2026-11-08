package com.roomelephant.porthole.versions.registry;

import java.util.List;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

final class RegistryTestClients {
    private RegistryTestClients() {}

    static RegistryOperationsAdapter operations(RestClient http, RegistryProperties properties) {
        var config = new RegistryConfiguration();
        var json = JsonMapper.builder().build();
        return new RegistryOperationsAdapter(
                List.of(config.dockerHubClient(http, json, properties), config.ghcrClient(http, json, properties)),
                properties.cache());
    }
}
