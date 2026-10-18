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
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class DockerConfig {
    private final DockerProperties properties;

    public DockerConfig(DockerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public DockerSourceRegistry dockerSources() {
        ArrayList<DockerConnection> sources = new ArrayList<>();
        try {
            for (DockerConnectionProperties p : properties.connections()) {
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
            for (DockerConnection s : sources)
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
