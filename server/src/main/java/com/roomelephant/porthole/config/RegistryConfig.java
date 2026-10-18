package com.roomelephant.porthole.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.port.outbound.RegistryOperations;
import com.roomelephant.porthole.outbound.registry.*;
import com.roomelephant.porthole.outbound.registry.distribution.*;
import com.roomelephant.porthole.outbound.registry.dockerhub.DockerHubClient;
import com.roomelephant.porthole.outbound.registry.ghcr.GhcrClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class RegistryConfig {

    @Bean
    public RegistryOperations registryOperations(List<RegistryClient> clients, RegistryProperties properties) {
        return new RegistryOperationsAdapter(clients, properties.cache());
    }

    @Bean
    public DockerHubClient dockerHubClient(RestClient client, ObjectMapper json, RegistryProperties properties) {
        DistributionClient distribution = new DistributionClient(
                client,
                json,
                properties.dockerhub().registryUrl(),
                new BearerTokenClient(client, json, properties.dockerhub().authUrl(), Clock.systemUTC()));
        return new DockerHubClient(
                client, json, properties.dockerhub(), properties.timeout().read(), distribution);
    }

    @Bean
    public GhcrClient ghcrClient(RestClient client, ObjectMapper json, RegistryProperties properties) {
        DistributionClient distribution = new DistributionClient(
                client,
                json,
                properties.ghcr().registryUrl(),
                new BearerTokenClient(client, json, properties.ghcr().authUrl(), Clock.systemUTC()));
        return new GhcrClient(properties.ghcr(), properties.timeout().read(), distribution);
    }

    @Bean
    public ObjectMapper registryJsonMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestClient restClient(RegistryProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout().connect())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout().read());

        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
