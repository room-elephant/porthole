package com.roomelephant.porthole.fleet.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.roomelephant.porthole.fleet.DockerConnectionProperties;
import com.roomelephant.porthole.fleet.DockerOperations;
import com.roomelephant.porthole.fleet.DockerProperties;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@NullMarked
@Configuration(proxyBeanMethods = false)
public class DockerConfiguration {
    private final DockerProperties properties;

    public DockerConfiguration(DockerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public DockerSourceRegistry dockerSources() {
        List<DockerConnection> connections = new ArrayList<>();
        try {
            for (DockerConnectionProperties p : properties.connections()) {
                connections.add(new DockerConnection(p.id(), createClient(p)));
            }
            return new DockerSourceRegistry(connections);
        } catch (RuntimeException e) {
            for (DockerConnection connection : connections)
                try {
                    connection.client().close();
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

    DockerClient createClient(DockerConnectionProperties p) {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(p.host())
                .build();
        ZerodepDockerHttpClient http = new ZerodepDockerHttpClient.Builder()
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
