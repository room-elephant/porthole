package com.roomelephant.porthole.it.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.domain.model.ContainerDTO;
import com.roomelephant.porthole.domain.model.EndpointDTO;
import com.roomelephant.porthole.it.infra.ContainerAwareIntegrationTestBase;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class PublishedHostOverrideIT extends ContainerAwareIntegrationTestBase {
    @Test
    void shouldUseConfiguredPublishedHost() {
        // The shared app owns hint recording; two agents must never write to the same directory.
        try (var app = new PortholeContainer(wireMockClient().port(), false)
                .withEnv("PORTHOLE_ENDPOINTS_PUBLISHEDHOST", "docker.example.com")) {
            app.start();
            var containers = new RestTemplate().getForObject(app.baseUrl() + "/api/containers", ContainerDTO[].class);
            var container = Arrays.stream(containers)
                    .filter(candidate -> candidate.name().equals(TEST_APP_CONTAINER_NAME))
                    .findFirst()
                    .orElseThrow();
            int port = testAppContainer.getMappedPort(8080);
            assertThat(container.endpoints())
                    .containsExactly(new EndpointDTO(
                            "published-port:" + port, "published-port", "http://docker.example.com:" + port + "/"));
            var proxy = Arrays.stream(containers)
                    .filter(candidate -> candidate.name().equals(TEST_PROXY_CONTAINER_NAME))
                    .findFirst()
                    .orElseThrow();
            assertThat(proxy.endpoints().getFirst().url()).isEqualTo("https://jellyfin.example.com/");
        }
    }
}
