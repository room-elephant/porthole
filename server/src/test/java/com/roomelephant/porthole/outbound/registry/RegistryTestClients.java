package com.roomelephant.porthole.outbound.registry;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.outbound.registry.dockerhub.DockerHubClient;
import com.roomelephant.porthole.outbound.registry.ghcr.GhcrClient;
import java.util.List;
import org.springframework.web.client.RestClient;

final class RegistryTestClients {
    private RegistryTestClients() {}

    static RegistryOperationsAdapter operations(RestClient restClient, RegistryProperties properties) {
        return new RegistryOperationsAdapter(
                List.of(
                        new DockerHubClient(
                                restClient,
                                properties.dockerhub(),
                                properties.timeout().read(),
                                properties.cache()),
                        new GhcrClient(
                                restClient,
                                properties.ghcr(),
                                properties.timeout().read())),
                properties.cache());
    }
}
