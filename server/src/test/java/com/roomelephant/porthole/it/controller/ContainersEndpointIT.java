package com.roomelephant.porthole.it.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import com.roomelephant.porthole.inbound.web.dto.ContainerDTO;
import com.roomelephant.porthole.inbound.web.dto.EndpointDTO;
import com.roomelephant.porthole.it.infra.ContainerAwareIntegrationTestBase;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ContainersEndpointIT extends ContainerAwareIntegrationTestBase {
    @Test
    void shouldShortenUniqueComposeNamesAndKeepCollidingReplicaSuffixes() {
        String project = "porthole-names-" + UUID.randomUUID();
        String uniqueName = project + "-sonarr-1";
        String replicaOneName = project + "-web-1";
        String replicaTwoName = project + "-web-2";
        Map<String, String> labels = Map.of("com.docker.compose.project", project);
        try (GenericContainer<?> unique = new GenericContainer<>(PAUSE_IMAGE)
                        .withCreateContainerCmdModifier(cmd -> cmd.withName(uniqueName))
                        .withLabels(labels)
                        .withExposedPorts(8080);
                GenericContainer<?> replicaOne = new GenericContainer<>(PAUSE_IMAGE)
                        .withCreateContainerCmdModifier(cmd -> cmd.withName(replicaOneName))
                        .withLabels(labels)
                        .withExposedPorts(8080);
                GenericContainer<?> replicaTwo = new GenericContainer<>(PAUSE_IMAGE)
                        .withCreateContainerCmdModifier(cmd -> cmd.withName(replicaTwoName))
                        .withLabels(labels)
                        .withExposedPorts(8080)) {
            unique.start();
            replicaOne.start();
            replicaTwo.start();

            Map<String, ContainerDTO> containers = fetchContainers(false, false);
            assertThat(containers.get(uniqueName).displayName()).isEqualTo("sonarr");
            assertThat(containers.get(replicaOneName).displayName()).isEqualTo("web-1");
            assertThat(containers.get(replicaTwoName).displayName()).isEqualTo("web-2");
            assertThat(containers.get(uniqueName).project()).isEqualTo(project);
        }
    }

    @Test
    void shouldReturnRunningContainers() {
        Map<String, ContainerDTO> containersByName = fetchContainers(false, false);

        assertThat(containersByName)
                .doesNotContainKey(TEST_NO_PORTS_CONTAINER_NAME)
                .doesNotContainKey(TEST_STOPPED_CONTAINER_NAME);

        ContainerDTO container = containersByName.get(TEST_APP_CONTAINER_NAME);
        assertThat(container).isNotNull();
        assertThat(container.id()).isNotBlank();
        assertThat(container.displayName()).isEqualTo(TEST_APP_CONTAINER_NAME);
        assertThat(container.image()).isEqualTo(PAUSE_IMAGE);
        assertThat(container.endpoints()).size().isEqualTo(1);
        assertThat(container.endpoints())
                .extracting(endpoint -> Integer.parseInt(endpoint.id().substring("published-port:".length())))
                .first()
                .isEqualTo(testAppContainer.getMappedPort(
                        testAppContainer.getExposedPorts().getFirst()));
        assertThat(container.iconUrl()).contains("/pause.");
        assertThat(container.state()).isIn("running", "created");
        assertThat(container.status()).contains("Up");
        assertThat(container.project()).isNull();
        assertThat(container.hasEndpoints()).isTrue();

        container = containersByName.get(TEST_LOCAL_CONTAINER_NAME);
        assertThat(container).isNotNull();
        assertThat(container.id()).isNotBlank();
        assertThat(container.displayName()).isEqualTo(TEST_LOCAL_CONTAINER_NAME);
        assertThat(container.image()).isEqualTo("my-local-image:1.0");
        assertThat(container.endpoints()).size().isEqualTo(1);
        assertThat(container.endpoints())
                .extracting(endpoint -> Integer.parseInt(endpoint.id().substring("published-port:".length())))
                .first()
                .isEqualTo(localContainer.getMappedPort(
                        localContainer.getExposedPorts().getFirst()));
        assertThat(container.iconUrl()).contains("/my-local-image.");
        assertThat(container.state()).isIn("running", "created");
        assertThat(container.status()).contains("Up");
        assertThat(container.project()).isNull();
        assertThat(container.hasEndpoints()).isTrue();
    }

    @Test
    void shouldShowContainersWithoutPorts() {
        Map<String, ContainerDTO> containersByName = fetchContainers(true, false);

        assertThat(containersByName)
                .containsKey(TEST_APP_CONTAINER_NAME)
                .containsKey(TEST_LOCAL_CONTAINER_NAME)
                .doesNotContainKey(TEST_STOPPED_CONTAINER_NAME);

        ContainerDTO container = containersByName.get(TEST_NO_PORTS_CONTAINER_NAME);
        assertThat(container).isNotNull();
        assertThat(container.id()).isNotBlank();
        assertThat(container.displayName()).isEqualTo(TEST_NO_PORTS_CONTAINER_NAME);
        assertThat(container.image()).isEqualTo(PAUSE_LATEST_IMAGE);
        assertThat(container.endpoints()).isEmpty();
        assertThat(container.iconUrl()).contains("/pause.");
        assertThat(container.state()).isEqualTo("running");
        assertThat(container.status()).contains("Up");
        assertThat(container.project()).isNull();
        assertThat(container.hasEndpoints()).isFalse();
    }

    @Test
    void shouldShowStoppedContainers() {
        // stopped container has no ports, so includeWithoutEndpoints must also be true for it to appear
        Map<String, ContainerDTO> containersByName = fetchContainers(true, true);

        assertThat(containersByName)
                .containsKey(TEST_APP_CONTAINER_NAME)
                .containsKey(TEST_LOCAL_CONTAINER_NAME)
                .containsKey(TEST_NO_PORTS_CONTAINER_NAME);

        ContainerDTO container = containersByName.get(TEST_STOPPED_CONTAINER_NAME);
        assertThat(container).isNotNull();
        assertThat(container.id()).isNotBlank();
        assertThat(container.displayName()).isEqualTo(TEST_STOPPED_CONTAINER_NAME);
        assertThat(container.image()).isEqualTo(PAUSE_IMAGE);
        assertThat(container.endpoints()).isEmpty();
        assertThat(container.iconUrl()).contains("/pause.");
        assertThat(container.state()).isIn("running", "created");
        assertThat(container.status()).isEqualTo("Created");
        assertThat(container.project()).isNull();
        assertThat(container.hasEndpoints()).isFalse();
    }

    @Test
    void shouldShowAllContainersWhenBothFlagsEnabled() {
        Map<String, ContainerDTO> containersByName = fetchContainers(true, true);

        assertThat(containersByName)
                .containsKey(TEST_APP_CONTAINER_NAME)
                .containsKey(TEST_LOCAL_CONTAINER_NAME)
                .containsKey(TEST_NO_PORTS_CONTAINER_NAME)
                .containsKey(TEST_STOPPED_CONTAINER_NAME);
    }

    @Test
    void shouldFlagOnlyPortholesOwnContainerAsSelf() {
        Map<String, ContainerDTO> containersByName = fetchContainers(true, true);

        assertThat(containersByName.values())
                .filteredOn(ContainerDTO::self)
                .extracting(ContainerDTO::id)
                .containsExactly(porthole.getContainerId());
    }

    @Test
    void shouldIncludeTraefikContainerWithoutPublishedPorts() {
        ContainerDTO container = fetchContainers(false, false).get(TEST_PROXY_CONTAINER_NAME);
        assertThat(container).isNotNull();
        assertThat(container.endpoints())
                .containsExactly(new EndpointDTO(
                        "traefik:https://jellyfin.example.com/", "traefik", "https://jellyfin.example.com/", true));
    }

    @Test
    void shouldIgnoreRequestHostForDefaultPublishedPortUrls() {
        startPorthole();
        for (String hostname : java.util.List.of("localhost", "127.0.0.1")) {
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("X-Forwarded-Host", "forwarded.example.com");
            var response = new org.springframework.web.client.RestTemplate()
                    .exchange(
                            "http://" + hostname + ":" + porthole.getMappedPort(9753) + "/api/containers",
                            org.springframework.http.HttpMethod.GET,
                            new org.springframework.http.HttpEntity<>(headers),
                            com.roomelephant.porthole.inbound.web.dto.ContainerInventoryDTO.class)
                    .getBody();
            assertThat(response).isNotNull();
            var container = response.containers().stream()
                    .filter(candidate -> candidate.name().equals(TEST_APP_CONTAINER_NAME))
                    .findFirst()
                    .orElseThrow();
            int port = testAppContainer.getMappedPort(8080);
            assertThat(container.endpoints())
                    .containsExactly(new EndpointDTO(
                            "published-port:" + port, "published-port", "http://127.0.0.1:" + port + "/", true));
        }
    }

    @Test
    void shouldReturnProxyAndPublishedPortCandidates() {
        ContainerDTO container = fetchContainers(false, false).get(TEST_MIXED_CONTAINER_NAME);
        int port = mixedContainer.getMappedPort(51413);
        assertThat(container.endpoints())
                .contains(new EndpointDTO(
                        "published-port:" + port, "published-port", "http://127.0.0.1:" + port + "/", false));
        assertThat(container.endpoints()).extracting(EndpointDTO::source).contains("traefik", "published-port");
    }

    @Test
    void shouldKeepDistinctRoutesAndDeduplicateEquivalentRouters() {
        ContainerDTO container = fetchContainers(false, false).get(TEST_MIXED_CONTAINER_NAME);
        assertThat(container.endpoints().stream()
                        .filter(endpoint -> endpoint.source().equals("traefik"))
                        .toList())
                .containsExactly(
                        new EndpointDTO(
                                "traefik:https://transmission.example.com/",
                                "traefik",
                                "https://transmission.example.com/",
                                true),
                        new EndpointDTO(
                                "traefik:https://transmission.example.com/dl/",
                                "traefik",
                                "https://transmission.example.com/dl/",
                                false));
    }

    @Test
    void shouldSkipDisabledAndUnsupportedRoutersWithoutLosingOtherCandidates() {
        Map<String, ContainerDTO> containers = fetchContainers(false, false);
        assertThat(containers).doesNotContainKey(TEST_UNSUPPORTED_CONTAINER_NAME);
        ContainerDTO disabled = containers.get(TEST_DISABLED_CONTAINER_NAME);
        assertThat(disabled.endpoints()).hasSize(1);
        assertThat(disabled.endpoints().getFirst().source()).isEqualTo("published-port");
        assertThat(containers.get(TEST_MIXED_CONTAINER_NAME).endpoints()).hasSize(3);
    }

    @Test
    void shouldFilterContainersWithoutEndpointsAndRespectStoppedFlag() {
        assertThat(fetchContainers(false, true))
                .doesNotContainKey(TEST_STOPPED_CONTAINER_NAME)
                .containsKey(TEST_PROXY_CONTAINER_NAME);
        assertThat(fetchContainers(true, false))
                .containsKeys(TEST_NO_PORTS_CONTAINER_NAME, TEST_UNSUPPORTED_CONTAINER_NAME)
                .doesNotContainKey(TEST_STOPPED_CONTAINER_NAME);
        assertThat(fetchContainers(true, true)).containsKey(TEST_STOPPED_CONTAINER_NAME);
        ResponseEntity<ContainerDTO[]> response =
                fetch("/api/containers?includeWithoutPorts=true", ContainerDTO[].class);
        assertThat(Arrays.stream(response.getBody()).map(ContainerDTO::name).toList())
                .doesNotContain(TEST_NO_PORTS_CONTAINER_NAME);
    }

    @Test
    void shouldSerializeOnlyEndpointContract() throws Exception {
        JsonNode body = new JsonMapper().readTree(fetch("/api/containers").getBody());
        boolean found = false;
        for (JsonNode container : body.path("containers")) {
            assertThat(container.has("preferences")).isFalse();
            assertThat(container.has("hasWebUi")).isTrue();
            assertThat(container.path("hasWebUi").isBoolean()).isTrue();
            assertThat(container.path("hasWebUi").asBoolean()).isTrue();
            assertThat(container.has("exposedPorts")).isFalse();
            assertThat(container.has("hasPublicPorts")).isFalse();
            assertThat(container.has("labels")).isFalse();
            assertThat(container.has("publishedPorts")).isFalse();
            assertThat(container.has("urlCandidates")).isFalse();
            assertThat(container.path("hasEndpoints").asBoolean()).isTrue();
            long selected = container
                    .path("endpoints")
                    .valueStream()
                    .filter(endpoint -> endpoint.path("selected").asBoolean())
                    .count();
            assertThat(selected).isLessThanOrEqualTo(1);
            for (JsonNode endpoint : container.path("endpoints")) {
                assertThat(endpoint.properties().stream().map(Map.Entry::getKey).toList())
                        .containsExactlyInAnyOrder("id", "source", "url", "selected");
                assertThat(endpoint.path("selected").isBoolean()).isTrue();
                assertThat(endpoint.path("url").asText()).startsWith("http");
            }
            if (container.path("name").asText().equals(TEST_PROXY_CONTAINER_NAME)) {
                found = true;
                assertThat(container.path("endpoints").get(0).path("url").asText())
                        .isEqualTo("https://jellyfin.example.com/");
            }
        }
        assertThat(found).isTrue();
    }

    private @NotNull Map<String, ContainerDTO> fetchContainers(
            boolean includeWithoutEndpoints, boolean includeStopped) {
        ResponseEntity<ContainerDTO @NotNull []> response = fetch(
                "/api/containers?includeWithoutEndpoints=" + includeWithoutEndpoints + "&includeStopped="
                        + includeStopped,
                ContainerDTO[].class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();

        return Arrays.stream(response.getBody()).collect(Collectors.toMap(ContainerDTO::name, Function.identity()));
    }
}
