package com.roomelephant.porthole.it.infra;

import com.github.dockerjava.api.model.ExposedPort;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class ContainerAwareIntegrationTestBase extends IntegrationTestBase {

    protected static final String TEST_LOCAL_IMAGE_TAG = "my-local-image:1.0";
    protected static final String TEST_APP_CONTAINER_NAME = "porthole-test-app";
    protected static final String TEST_NO_PORTS_CONTAINER_NAME = "porthole-test-no-ports";
    protected static final String TEST_STOPPED_CONTAINER_NAME = "porthole-test-stopped";
    protected static final String TEST_LOCAL_CONTAINER_NAME = "porthole-test-local";
    protected static final String PAUSE_IMAGE = "registry.k8s.io/pause:3.9";
    protected static final String PAUSE_LATEST_IMAGE = "registry.k8s.io/pause:latest";

    protected static final GenericContainer<?> testAppContainer;
    protected static final GenericContainer<?> localContainer;
    protected static final GenericContainer<?> noPortsContainer;

    static {
        var dc = DockerClientFactory.instance().client();

        try {
            dc.pullImageCmd(PAUSE_IMAGE).start().awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while pulling images", e);
        }

        dc.tagImageCmd(PAUSE_IMAGE, "my-local-image", "1.0").exec();
        dc.tagImageCmd(PAUSE_IMAGE, "registry.k8s.io/pause", "latest").exec();

        testAppContainer = new GenericContainer<>(PAUSE_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_APP_CONTAINER_NAME))
                .withExposedPorts(8080);
        testAppContainer.start();

        // Uses raw docker-java because GenericContainer.stop() also removes the container
        try {
            dc.removeContainerCmd(TEST_STOPPED_CONTAINER_NAME).withForce(true).exec();
        } catch (Exception ignored) {
        }
        dc.createContainerCmd(PAUSE_IMAGE)
                .withName(TEST_STOPPED_CONTAINER_NAME)
                .withExposedPorts(ExposedPort.tcp(8081))
                .exec();

        localContainer = new GenericContainer<>(DockerImageName.parse(TEST_LOCAL_IMAGE_TAG))
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_LOCAL_CONTAINER_NAME))
                .withExposedPorts(8082)
                .withImagePullPolicy(imageName -> false);
        localContainer.start();

        noPortsContainer = new GenericContainer<>(PAUSE_LATEST_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_NO_PORTS_CONTAINER_NAME))
                .withImagePullPolicy(imageName -> false);
        noPortsContainer.start();
    }
}
