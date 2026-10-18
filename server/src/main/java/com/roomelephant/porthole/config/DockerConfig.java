package com.roomelephant.porthole.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.roomelephant.porthole.config.properties.*;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import com.roomelephant.porthole.outbound.docker.DockerConnection;
import com.roomelephant.porthole.outbound.docker.DockerJavaOperations;
import com.roomelephant.porthole.outbound.docker.DockerSourceRegistry;
import java.util.ArrayList;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class DockerConfig {
    private final DockerProperties properties;

    public DockerConfig(DockerProperties properties, Environment environment) {
        rejectLegacyProperties(environment);
        this.properties = properties;
    }

    @Bean
    public DockerSourceRegistry dockerSources() {
        var sources = new ArrayList<DockerConnection>();
        try {
            for (var p : properties.connections()) {
                sources.add(new DockerConnection(
                        p.id(),
                        p.name(),
                        p.publishedHost(),
                        p.connectionTimeout(),
                        p.responseTimeout(),
                        createClient(p)));
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
    public DockerOperations dockerOperations(DockerSourceRegistry sources) {
        return new DockerJavaOperations(sources);
    }

    private void rejectLegacyProperties(Environment environment) {
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
        if (binder.bind("porthole.endpoints.published-host", String.class).isBound()) {
            throw new IllegalArgumentException(
                    "Global setting porthole.endpoints.published-host is no longer supported; "
                            + "configure published-host in porthole.docker.connections instead");
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
}
