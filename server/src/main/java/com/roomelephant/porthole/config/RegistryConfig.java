package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.port.outbound.RegistryOperations;
import com.roomelephant.porthole.outbound.registry.HttpRegistryOperations;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class RegistryConfig {

    @Bean
    public RegistryOperations registryOperations(RestClient client, RegistryProperties properties) {
        return new HttpRegistryOperations(client, properties);
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
