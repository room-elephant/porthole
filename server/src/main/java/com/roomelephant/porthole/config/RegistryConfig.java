package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.port.outbound.RegistryOperations;
import com.roomelephant.porthole.outbound.registry.*;
import com.roomelephant.porthole.outbound.registry.dockerhub.DockerHubClient;
import com.roomelephant.porthole.outbound.registry.ghcr.GhcrClient;
import java.net.http.HttpClient;
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
    public DockerHubClient dockerHubClient(RestClient client, RegistryProperties properties) {
        return new DockerHubClient(
                client, properties.dockerhub(), properties.timeout().read(), properties.cache());
    }

    @Bean
    public GhcrClient ghcrClient(RestClient client, RegistryProperties properties) {
        return new GhcrClient(client, properties.ghcr(), properties.timeout().read());
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
