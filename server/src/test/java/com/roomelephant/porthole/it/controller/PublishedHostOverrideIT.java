package com.roomelephant.porthole.it.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.controller.model.ContainerDTO;
import com.roomelephant.porthole.controller.model.EndpointDTO;
import com.roomelephant.porthole.it.infra.ContainerAwareIntegrationTestBase;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class PublishedHostOverrideIT extends ContainerAwareIntegrationTestBase {
    @Test
    void shouldUseConfiguredPublishedHost() {
        try (PortholeContainer app =
                porthole.withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_PUBLISHEDHOST", "docker.example.com")) {
            app.start();
            ContainerDTO[] containers = new RestTemplate()
                    .getForObject(
                            app.baseUrl() + "/api/containers",
                            com.roomelephant.porthole.controller.model.ContainerInventoryDTO.class)
                    .containers()
                    .toArray(ContainerDTO[]::new);
            ContainerDTO container = Arrays.stream(containers)
                    .filter(candidate -> candidate.name().equals(TEST_APP_CONTAINER_NAME))
                    .findFirst()
                    .orElseThrow();
            int port = testAppContainer.getMappedPort(8080);
            assertThat(container.endpoints())
                    .containsExactly(new EndpointDTO(
                            "published-port:" + port, "published-port", "http://docker.example.com:" + port + "/"));
            ContainerDTO proxy = Arrays.stream(containers)
                    .filter(candidate -> candidate.name().equals(TEST_PROXY_CONTAINER_NAME))
                    .findFirst()
                    .orElseThrow();
            assertThat(proxy.endpoints().getFirst().url()).isEqualTo("https://jellyfin.example.com/");
        }
    }
}
