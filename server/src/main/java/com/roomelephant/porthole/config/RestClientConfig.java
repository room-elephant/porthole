package com.roomelephant.porthole.config;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private final RegistryProperties registryProperties;

    public RestClientConfig(RegistryProperties registryProperties) {
        this.registryProperties = registryProperties;
    }

    @Bean
    public RestClient restClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(registryProperties.timeout().connect())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(registryProperties.timeout().read());

        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
