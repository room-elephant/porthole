package com.roomelephant.porthole.it.infra;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class PortholeContainer extends GenericContainer<PortholeContainer> {

    public static final int PORTHOLE_PORT = 9753;
    static final String IMAGE_TAG = "porthole-it:latest";

    public PortholeContainer(int wireMockPort) {
        super(IMAGE_TAG);
        withExposedPorts(PORTHOLE_PORT);
        withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock", BindMode.READ_WRITE);
        withEnv("PORTHOLE_DOCKER_HOST", "unix:///var/run/docker.sock");
        applyRegistryEnv(this, wireMockPort);
        applyNativeAgentIfEnabled(this);
        waitingFor(Wait.forHttp("/actuator/health").forPort(PORTHOLE_PORT));
    }

    public String baseUrl() {
        return "http://localhost:" + getMappedPort(PORTHOLE_PORT);
    }

    /**
     * Factory for resilience-test Porthole instances that connect to a custom socket path.
     * Does NOT mount /var/run/docker.sock — caller must add their own socket bind.
     */
    public static GenericContainer<?> withCustomSocket(String socketPath, int wireMockPort) {
        GenericContainer<?> container = new GenericContainer<>(IMAGE_TAG)
                .withExposedPorts(PORTHOLE_PORT)
                .withEnv("PORTHOLE_DOCKER_HOST", "unix://" + socketPath);
        applyRegistryEnv(container, wireMockPort);
        container.waitingFor(
                Wait.forHttp("/actuator/health").forPort(PORTHOLE_PORT).forStatusCodeMatching(status -> status >= 100));
        return container;
    }

    private static void applyRegistryEnv(GenericContainer<?> container, int wireMockPort) {
        String base = "http://host.docker.internal:" + wireMockPort;
        container
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("REGISTRY_URLS_REGISTRY", base + "/v2/")
                .withEnv("REGISTRY_URLS_AUTH", base + "/auth?service=registry.docker.io&scope=repository:")
                .withEnv("REGISTRY_URLS_REPOSITORIES", base + "/v2/repositories/")
                .withEnv("REGISTRY_CACHE_TTL", "1ms")
                .withEnv("REGISTRY_CACHE_VERSION_MAX_SIZE", "1");
    }

    private static void applyNativeAgentIfEnabled(GenericContainer<?> container) {
        String outputDir = System.getProperty("native.agent.output.dir");
        if (outputDir != null) {
            container.withEnv("JAVA_TOOL_OPTIONS", "-agentlib:native-image-agent=config-output-dir=/tmp/native-hints");
            container.withFileSystemBind(outputDir, "/tmp/native-hints", BindMode.READ_WRITE);
        }
    }
}
