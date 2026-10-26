package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.port.outbound.RegistryOperations;
import com.roomelephant.porthole.outbound.registry.*;
import com.roomelephant.porthole.outbound.registry.distribution.*;
import com.roomelephant.porthole.outbound.registry.dockerhub.DockerHubClient;
import com.roomelephant.porthole.outbound.registry.ghcr.GhcrClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class RegistryConfig {

    @Bean
    public @NonNull RegistryOperations registryOperations(
            @NonNull List<RegistryClient> clients, @NonNull RegistryProperties properties) {
        return new RegistryOperationsAdapter(clients, properties.cache());
    }

    @Bean
    public @NonNull DockerHubClient dockerHubClient(
            @NonNull RestClient client, @NonNull JsonMapper json, @NonNull RegistryProperties properties) {
        DistributionClient distribution = new DistributionClient(
                client,
                json,
                properties.dockerhub().registryUrl(),
                new BearerTokenClient(client, json, properties.dockerhub().authUrl(), Clock.systemUTC()));
        return new DockerHubClient(
                client, json, properties.dockerhub(), properties.timeout().read(), distribution);
    }

    @Bean
    public @NonNull GhcrClient ghcrClient(
            @NonNull RestClient client, @NonNull JsonMapper json, @NonNull RegistryProperties properties) {
        DistributionClient distribution = new DistributionClient(
                client,
                json,
                properties.ghcr().registryUrl(),
                new BearerTokenClient(client, json, properties.ghcr().authUrl(), Clock.systemUTC()));
        return new GhcrClient(properties.ghcr(), properties.timeout().read(), distribution);
    }

    @Bean
    public @NonNull RestClient restClient(@NonNull RegistryProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout().connect())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout().read());

        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
