package com.roomelephant.porthole.it.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class PortholeContainer extends GenericContainer<PortholeContainer> {

    public static final int PORTHOLE_PORT = 9753;
    static final String IMAGE_TAG = System.getProperty("porthole.it.image", "porthole-jvm:latest");

    private static final List<PortholeContainer> recordingContainers = new CopyOnWriteArrayList<>();
    private final boolean recordingNativeHints;

    public PortholeContainer(int wireMockPort) {
        this(wireMockPort, "/var/run/docker.sock");
        withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock", BindMode.READ_WRITE);
        waitingFor(Wait.forHttp("/actuator/health").forPort(PORTHOLE_PORT));
    }

    private PortholeContainer(int wireMockPort, String socketPath) {
        super(IMAGE_TAG);
        withExposedPorts(PORTHOLE_PORT);
        withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_ID", "local");
        withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_NAME", "Local");
        withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_HOST", "unix://" + socketPath);
        applyRegistryEnv(this, wireMockPort);
        recordingNativeHints = applyNativeAgentIfEnabled();
        if (recordingNativeHints) recordingContainers.add(this);
    }

    @Override
    public void stop() {
        try {
            if (recordingNativeHints && isRunning()) {
                // Flush the agent on JVM exit before Testcontainers removes the container.
                getDockerClient()
                        .stopContainerCmd(getContainerId())
                        .withTimeout(30)
                        .exec();
            }
        } finally {
            super.stop();
        }
    }

    static void stopRecordingContainers() {
        for (PortholeContainer container : recordingContainers) container.stop();
        recordingContainers.clear();
    }

    public String baseUrl() {
        return "http://localhost:" + getMappedPort(PORTHOLE_PORT);
    }

    /**
     * Factory for resilience-test Porthole instances that connect to a custom socket path.
     * Does NOT mount /var/run/docker.sock — caller must add their own socket bind.
     */
    public static GenericContainer<?> withCustomSocket(String socketPath, int wireMockPort) {
        PortholeContainer container = new PortholeContainer(wireMockPort, socketPath);
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
                .withEnv("REGISTRY_GHCR_REGISTRY_URL", base + "/ghcr/v2/")
                .withEnv("REGISTRY_GHCR_AUTH_URL", base + "/ghcr/token?service=ghcr.io&scope=repository:")
                .withEnv("REGISTRY_CACHE_TTL", "1ms")
                .withEnv("REGISTRY_CACHE_VERSION_MAX_SIZE", "1");
    }

    private boolean applyNativeAgentIfEnabled() {
        String outputDir = System.getProperty("native.agent.output.dir");
        if (outputDir == null) return false;
        try {
            Path root = Path.of(outputDir).toAbsolutePath();
            Files.createDirectories(root);
            Path recording = Files.createTempDirectory(root, "app-");
            // The resilience containers run as UID 65532 rather than root.
            if (!recording.toFile().setWritable(true, false)
                    || !recording.toFile().setReadable(true, false)
                    || !recording.toFile().setExecutable(true, false)) {
                throw new IllegalStateException("Cannot make recording directory accessible: " + recording);
            }
            withEnv(
                    "JAVA_TOOL_OPTIONS",
                    "-agentlib:native-image-agent=config-output-dir=/tmp/native-hints,config-write-period-secs=10");
            withFileSystemBind(recording.toString(), "/tmp/native-hints", BindMode.READ_WRITE);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create native hint recording directory", e);
        }
    }
}
