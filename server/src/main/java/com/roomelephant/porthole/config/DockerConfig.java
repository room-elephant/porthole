package com.roomelephant.porthole.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.roomelephant.porthole.config.properties.*;
import com.roomelephant.porthole.domain.component.DockerSourceRegistry;
import com.roomelephant.porthole.domain.model.DockerSource;
import java.util.ArrayList;
import org.springframework.context.annotation.*;

@Configuration
public class DockerConfig {
    private final DockerProperties properties;
    private final EndpointProperties endpoints;

    public DockerConfig(DockerProperties properties, EndpointProperties endpoints) {
        this.properties = properties;
        this.endpoints = endpoints;
    }

    @Bean
    public DockerSourceRegistry dockerSources() {
        var sources = new ArrayList<DockerSource>();
        try {
            for (var p : properties.connections()) {
                String published = p.publishedHost();
                if (published == null && properties.connections().size() == 1) published = endpoints.publishedHost();
                sources.add(new DockerSource(
                        p.id(), p.name(), published, p.connectionTimeout(), p.responseTimeout(), createClient(p)));
            }
            return new DockerSourceRegistry(sources);
        } catch (RuntimeException e) {
            for (var s : sources)
                try {
                    s.client().close();
                } catch (Exception cleanup) {
                    e.addSuppressed(cleanup);
                }
            throw e;
        }
    }

    DockerClient createClient(DockerConnectionProperties p) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(p.host())
                .build();
        var http = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(p.connectionTimeout())
                .responseTimeout(p.responseTimeout())
                .build();
        try {
            return DockerClientImpl.getInstance(config, http);
        } catch (RuntimeException e) {
            try {
                http.close();
            } catch (Exception close) {
                e.addSuppressed(close);
            }
            throw e;
        }
    }
    // Transitional bean removed when all consumers select their source explicitly.
    @Bean(destroyMethod = "")
    public DockerClient dockerClient(DockerSourceRegistry sources) {
        return sources.sources().getFirst().client();
    }
}
