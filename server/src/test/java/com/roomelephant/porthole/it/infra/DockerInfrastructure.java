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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import lombok.SneakyThrows;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public class DockerInfrastructure implements AutoCloseable {

    @Override
    public void close() {
        docker.stop();
    }

    public static final int DIND_PORT = 2375;
    public static final String[] CONTAINER_CMD = {"sh", "-c", "echo started; while true; do sleep 3600; done"};

    private final GenericContainer<?> docker;
    private final DockerClient sharedDockerClient;

    public DockerInfrastructure() {
        String ci = System.getenv("CI");
        String dockerCachePath =
                "true".equalsIgnoreCase(ci) ? System.getenv("DOCKER_CACHE_PATH") : "porthole-dind-data";

        docker = new GenericContainer<>("docker:29.1.4-dind")
                .withPrivilegedMode(true)
                .withExposedPorts(DIND_PORT)
                .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(cmd.getHostConfig()
                        .withPortBindings(
                                new PortBinding(Ports.Binding.bindPort(DIND_PORT), ExposedPort.tcp(DIND_PORT)))
                        .withBinds(new Bind(dockerCachePath, new Volume("/var/lib/docker")))))
                .withEnv("DOCKER_TLS_CERTDIR", "")
                .withSharedMemorySize(512L * 1024 * 1024) // 512MB
                .waitingFor(forLogMessage(".*API listen on \\[::\\]:2375.*", 1));

        docker.start();
        sharedDockerClient = createClient(getDinDHost());
    }

    public DockerClient getDetailsDockerClient() {
        return sharedDockerClient;
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
                .withTags(java.util.Set.of(tag))
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

    private String getDinDHost() {
        return "tcp://" + docker.getHost() + ":" + docker.getMappedPort(DIND_PORT);
    }
}
