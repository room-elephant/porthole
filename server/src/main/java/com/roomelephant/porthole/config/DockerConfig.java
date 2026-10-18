package com.roomelephant.porthole.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.roomelephant.porthole.config.properties.*;
import com.roomelephant.porthole.outbound.docker.DockerConnection;
import com.roomelephant.porthole.outbound.docker.DockerSourceRegistry;
import java.util.ArrayList;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class DockerConfig {
    private final DockerProperties properties;
    private final EndpointProperties endpoints;

    public DockerConfig(DockerProperties properties, EndpointProperties endpoints, Environment environment) {
        // Strict binding excludes real environment/system-property sources from unknown-key checks.
        var binder = Binder.get(environment);
        for (String key : new String[] {"host", "connection-timeout", "response-timeout"}) {
            String legacy = "porthole.docker." + key;
            if (binder.bind(legacy, String.class).isBound()) {
                throw new IllegalArgumentException(
                        "Legacy setting " + legacy
                                + " is no longer supported; migrate to porthole.docker.connections with id, name and host per connection");
            }
        }
        this.properties = properties;
        this.endpoints = endpoints;
    }

    @Bean
    public DockerSourceRegistry dockerSources() {
        var sources = new ArrayList<DockerConnection>();
        try {
            for (var p : properties.connections()) {
                String published = p.publishedHost();
                if (published == null && properties.connections().size() == 1) published = endpoints.publishedHost();
                sources.add(new DockerConnection(
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

    @Bean
    public com.roomelephant.porthole.core.port.outbound.DockerOperations dockerOperations(
            DockerSourceRegistry sources) {
        return new com.roomelephant.porthole.outbound.docker.DockerJavaOperations(sources);
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
}
