package com.roomelephant.porthole.fleet.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.roomelephant.porthole.fleet.DockerConnectionProperties;
import com.roomelephant.porthole.fleet.DockerOperations;
import com.roomelephant.porthole.fleet.DockerProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
        List<DockerClient> created = new ArrayList<>();
        try {
            for (DockerConnectionProperties p : properties.connections()) {
                DockerClient client = createClient(p, p.responseTimeout());
                created.add(client);
                DockerClient events = createClient(p, null);
                created.add(events);
                connections.add(new DockerConnection(p.id(), client, events));
            }
            return new DockerSourceRegistry(connections);
        } catch (RuntimeException e) {
            for (DockerClient client : created)
                try {
                    client.close();
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

    /** A client for the source; a null {@code responseTimeout} lets responses take as long as they need. */
    DockerClient createClient(DockerConnectionProperties p, @Nullable Duration responseTimeout) {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(p.host())
                .build();
        ZerodepDockerHttpClient.Builder builder = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(p.connectionTimeout());
        if (responseTimeout != null) builder.responseTimeout(responseTimeout);
        ZerodepDockerHttpClient http = builder.build();
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
