package com.roomelephant.porthole.it.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import com.roomelephant.porthole.domain.model.ContainerDTO;
import com.roomelephant.porthole.it.infra.IntegrationTestBase;
import com.roomelephant.porthole.it.infra.RunWithoutContainers;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.ResponseEntity;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(3)
class ContainersEndpointIT extends IntegrationTestBase {
    @Test
    @Order(1)
    @RunWithoutContainers
    void shouldReturnEmptyListWhenNoContainers() {
        Map<String, ContainerDTO> containers = fetchContainers(true, true);

        assertThat(containers).size().isEqualTo(0);
    }

    @Test
    void shouldReturnRunningContainers() {
        Map<String, ContainerDTO> containersByName = fetchContainers(false, false);

        assertThat(containersByName).hasSize(2);

        ContainerDTO container = containersByName.get(TEST_APP_CONTAINER_NAME);
        assertThat(container).isNotNull();
        assertThat(container.displayName()).isEqualTo(TEST_APP_CONTAINER_NAME);
        assertThat(container.image()).isEqualTo(BUSYBOX_IMAGE);
        assertThat(container.exposedPorts()).size().isEqualTo(1);
        assertThat(container.exposedPorts())
                .first()
                .isEqualTo(testAppContainer.getMappedPort(
                        testAppContainer.getExposedPorts().getFirst()));
        assertThat(container.iconUrl()).contains("/busybox.");
        assertThat(container.state()).isIn("running", "created");
        assertThat(container.status()).contains("Up");
        assertThat(container.project()).isNull();
        assertThat(container.hasPublicPorts()).isTrue();

        container = containersByName.get(TEST_LOCAL_CONTAINER_NAME);
        assertThat(container).isNotNull();
        assertThat(container.displayName()).isEqualTo(TEST_LOCAL_CONTAINER_NAME);
        assertThat(container.image()).isEqualTo("my-local-image:1.0");
        assertThat(container.exposedPorts()).size().isEqualTo(1);
        assertThat(container.exposedPorts())
                .first()
                .isEqualTo(localContainer.getMappedPort(
                        localContainer.getExposedPorts().getFirst()));
        assertThat(container.iconUrl()).contains("/my-local-image.");
        assertThat(container.state()).isIn("running", "created");
        assertThat(container.status()).contains("Up");
        assertThat(container.project()).isNull();
        assertThat(container.hasPublicPorts()).isTrue();
    }

    @Test
    void shouldShowContainersWithoutPorts() {
        Map<String, ContainerDTO> containersByName = fetchContainers(false, false);

        assertThat(containersByName).hasSize(2);
        assertThat(containersByName.get(TEST_NO_PORTS_CONTAINER_NAME)).isNull();

        containersByName = fetchContainers(true, false);

        assertThat(containersByName).hasSize(3);

        ContainerDTO container = containersByName.get(TEST_NO_PORTS_CONTAINER_NAME);
        assertThat(container).isNotNull();
        assertThat(container.displayName()).isEqualTo(TEST_NO_PORTS_CONTAINER_NAME);
        assertThat(container.image()).isEqualTo(BUSYBOX_LATEST_IMAGE);
        assertThat(container.exposedPorts()).isEmpty();
        assertThat(container.iconUrl()).contains("/busybox.");
        assertThat(container.state()).isEqualTo("running");
        assertThat(container.status()).contains("Up");
        assertThat(container.project()).isNull();
        assertThat(container.hasPublicPorts()).isFalse();
    }

    @Test
    void shouldShowStoppedContainers() {
        Map<String, ContainerDTO> containersByName = fetchContainers(false, false);

        assertThat(containersByName).hasSize(2);
        assertThat(containersByName.get(TEST_STOPPED_CONTAINER_NAME)).isNull();

        containersByName = fetchContainers(true, true);

        assertThat(containersByName).hasSize(4);

        ContainerDTO container = containersByName.get(TEST_STOPPED_CONTAINER_NAME);
        assertThat(container).isNotNull();
        assertThat(container.displayName()).isEqualTo(TEST_STOPPED_CONTAINER_NAME);
        assertThat(container.image()).isEqualTo(BUSYBOX_IMAGE);
        assertThat(container.exposedPorts()).isEmpty();
        assertThat(container.iconUrl()).contains("/busybox.");
        assertThat(container.state()).isIn("running", "created");
        assertThat(container.status()).isEqualTo("Created");
        assertThat(container.project()).isNull();
        assertThat(container.hasPublicPorts()).isFalse();
    }

    private @NotNull Map<String, ContainerDTO> fetchContainers(boolean includeWithoutPorts, boolean includeStopped) {
        ResponseEntity<ContainerDTO @NotNull []> response = fetch(
                "/api/containers?includeWithoutPorts=" + includeWithoutPorts + "&includeStopped=" + includeStopped,
                ContainerDTO[].class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();

        return Arrays.stream(response.getBody()).collect(Collectors.toMap(ContainerDTO::name, Function.identity()));
    }
}
