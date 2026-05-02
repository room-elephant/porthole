package com.roomelephant.porthole.it.infra;

import static org.testcontainers.containers.wait.strategy.Wait.forLogMessage;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class DockerInfrastructure implements AutoCloseable {

    @Override
    public void close() {
        docker.stop();
        network.close();
    }

    public static final int DIND_PORT = 2375;
    public static final String PORTHOLE_IT_IMAGE_TAG = "porthole-it:latest";
    public static final String[] CONTAINER_CMD = {"sh", "-c", "echo started; while true; do sleep 3600; done"};

    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

    private final Network network = Network.newNetwork();
    private final GenericContainer<?> docker;
    private final DockerClient sharedDockerClient;
    private boolean portholeImageBuilt = false;

    public DockerInfrastructure() {
        String ci = System.getenv("CI");
        int instanceId = INSTANCE_COUNTER.getAndIncrement();
        String dockerCachePath = "true".equalsIgnoreCase(ci)
                ? System.getenv("DOCKER_CACHE_PATH") + "/instance-" + instanceId
                : "porthole-dind-" + instanceId;

        docker = new GenericContainer<>("docker:29.1.4-dind")
                .withPrivilegedMode(true)
                .withNetwork(network)
                .withNetworkAliases("dind")
                .withExposedPorts(DIND_PORT, 9753)
                .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(cmd.getHostConfig()
                        .withPortBindings(
                                new PortBinding(Ports.Binding.empty(), ExposedPort.tcp(DIND_PORT)),
                                new PortBinding(Ports.Binding.empty(), ExposedPort.tcp(9753)))
                        .withBinds(new Bind(dockerCachePath, new Volume("/var/lib/docker")))))
                .withEnv("DOCKER_TLS_CERTDIR", "")
                .withSharedMemorySize(512L * 1024 * 1024) // 512MB
                .waitingFor(forLogMessage(".*API listen on \\[::\\]:2375.*", 1));

        docker.start();
        sharedDockerClient = createClient(getDinDTcpUrl());
    }

    public DockerClient getDetailsDockerClient() {
        return sharedDockerClient;
    }

    public DockerClient getDinDClient() {
        return sharedDockerClient;
    }

    public String getDinDHost() {
        return docker.getHost();
    }

    public int getDinDMappedPort9753() {
        return docker.getMappedPort(9753);
    }

    public void removeContainerQuietly(String containerName) {
        try {
            sharedDockerClient.removeContainerCmd(containerName).withForce(true).exec();
        } catch (Exception _) {
            // Ignore
        }
    }

    @SneakyThrows
    public void pullImage(String imageName) {
        sharedDockerClient.pullImageCmd(imageName).start().awaitCompletion();
    }

    public void buildStoppedImage(String image, int port, String name) {
        sharedDockerClient
                .createContainerCmd(image)
                .withExposedPorts(ExposedPort.tcp(port))
                .withName(name)
                .exec();
    }

    @SneakyThrows
    public GenericContainer<?> buildLocalImage(String baseImage, Integer port, String name, String tag) {
        Path tempDir = Files.createTempDirectory("porthole-test-context");
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM " + baseImage);

        sharedDockerClient
                .buildImageCmd(tempDir.toFile())
                .withTags(Set.of(tag))
                .start()
                .awaitImageId();

        return buildImage(tag, port, name, true);
    }

    public GenericContainer<?> buildImage(String image, Integer port, String name) {
        return buildImage(image, port, name, false);
    }

    public GenericContainer<?> buildImage(String image, Integer port, String name, boolean local) {
        var temp = new DinDContainer<>(DockerImageName.parse(image), sharedDockerClient)
                .withCommand(CONTAINER_CMD)
                .waitingFor(new AbstractWaitStrategy() {
                    @Override
                    protected void waitUntilReady() {
                        // No-op
                    }
                })
                .withStartupTimeout(Duration.ofSeconds(120))
                .withCreateContainerCmdModifier(cmd -> cmd.withName(name));

        if (port != null) {
            temp.withExposedPorts(port);
        }
        if (local) {
            temp.withImagePullPolicy(imageName -> false);
        }

        return temp;
    }

    public void unpauseDocker() {
        docker.getDockerClient().unpauseContainerCmd(docker.getContainerId()).exec();
    }

    public void pauseDocker() {
        docker.getDockerClient().pauseContainerCmd(docker.getContainerId()).exec();
    }

    public GenericContainer<?> startWireMock() {
        GenericContainer<?> wireMock = new GenericContainer<>("wiremock/wiremock:3.13.0")
                .withNetwork(network)
                .withNetworkAliases("wiremock")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/__admin/health").forPort(8080));
        wireMock.start();
        return wireMock;
    }

    // TODO: PortholeContainer created in Task 3
    public PortholeContainer startPorthole(String wireMockIp) {
        PortholeContainer porthole = new PortholeContainer(this, wireMockIp);
        porthole.start();
        return porthole;
    }

    public synchronized void ensurePortholeImageBuilt() {
        if (portholeImageBuilt) {
            return;
        }
        Path nativeBinary = Path.of(System.getProperty("user.dir")).getParent().resolve("server/target/porthole");
        if (isLinuxElf(nativeBinary)) {
            buildPortholeImageFromBinary();
        } else {
            buildPortholeImageFromSource();
        }
        portholeImageBuilt = true;
    }

    @SneakyThrows
    private void buildPortholeImageFromBinary() {
        Path projectRoot = Path.of(System.getProperty("user.dir")).getParent();
        Path buildContext = Files.createTempDirectory("porthole-it-build-context");

        Files.copy(
                projectRoot.resolve("server/target/porthole"),
                buildContext.resolve("porthole"),
                StandardCopyOption.REPLACE_EXISTING);

        Path dockerDir = buildContext.resolve("docker");
        Files.createDirectories(dockerDir);
        Files.copy(
                projectRoot.resolve("docker/entrypoint.sh"),
                dockerDir.resolve("entrypoint.sh"),
                StandardCopyOption.REPLACE_EXISTING);
        copyDirectory(projectRoot.resolve("docker/templates"), dockerDir.resolve("templates"));
        Files.copy(
                projectRoot.resolve("docker/Dockerfile"),
                buildContext.resolve("Dockerfile"),
                StandardCopyOption.REPLACE_EXISTING);

        sharedDockerClient
                .buildImageCmd(buildContext.toFile())
                .withTags(Set.of(PORTHOLE_IT_IMAGE_TAG))
                .start()
                .awaitImageId();
    }

    @SneakyThrows
    private void buildPortholeImageFromSource() {
        Path projectRoot = Path.of(System.getProperty("user.dir")).getParent();
        Path buildContext = Files.createTempDirectory("porthole-it-build-context");

        Path serverDir = buildContext.resolve("server");
        Files.createDirectories(serverDir);
        Files.copy(
                projectRoot.resolve("server/pom.xml"),
                serverDir.resolve("pom.xml"),
                StandardCopyOption.REPLACE_EXISTING);
        copyDirectory(projectRoot.resolve("server/src"), serverDir.resolve("src"));

        Path dockerDir = buildContext.resolve("docker");
        Files.createDirectories(dockerDir);
        Files.copy(
                projectRoot.resolve("docker/entrypoint.sh"),
                dockerDir.resolve("entrypoint.sh"),
                StandardCopyOption.REPLACE_EXISTING);
        copyDirectory(projectRoot.resolve("docker/templates"), dockerDir.resolve("templates"));
        Files.copy(
                projectRoot.resolve("docker/Dockerfile.it"),
                buildContext.resolve("Dockerfile"),
                StandardCopyOption.REPLACE_EXISTING);

        sharedDockerClient
                .buildImageCmd(buildContext.toFile())
                .withTags(Set.of(PORTHOLE_IT_IMAGE_TAG))
                .start()
                .awaitImageId();
    }

    @SneakyThrows
    private static void copyDirectory(Path source, Path target) {
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Files.copy(src, target.resolve(source.relativize(src)), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static boolean isLinuxElf(Path path) {
        if (!Files.exists(path)) return false;
        try (InputStream is = Files.newInputStream(path)) {
            byte[] magic = is.readNBytes(4);
            return magic.length == 4
                    && magic[0] == 0x7F
                    && magic[1] == (byte) 'E'
                    && magic[2] == (byte) 'L'
                    && magic[3] == (byte) 'F';
        } catch (IOException e) {
            return false;
        }
    }

    private DockerClient createClient(String dockerHost) {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerTlsVerify(false)
                .build();

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        return DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
    }

    private String getDinDTcpUrl() {
        return "tcp://" + docker.getHost() + ":" + docker.getMappedPort(DIND_PORT);
    }
}
