package com.roomelephant.porthole.it.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.it.infra.ContainerAwareIntegrationTestBase;
import com.roomelephant.porthole.it.infra.DashboardView;
import com.roomelephant.porthole.it.infra.DashboardView.ContainerView;
import com.roomelephant.porthole.it.infra.DashboardView.EndpointView;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class PublishedHostOverrideIT extends ContainerAwareIntegrationTestBase {
    @Test
    void shouldUseConfiguredPublishedHost() {
        try (PortholeContainer app =
                porthole.withEnv("PORTHOLE_DOCKER_CONNECTIONS_0_PUBLISHEDHOST", "docker.example.com")) {
            app.start();
            java.util.List<ContainerView> containers = DashboardView.parse(
                            new RestTemplate().getForObject(app.baseUrl() + DashboardView.PATH, String.class))
                    .containers();
            ContainerView container = containers.stream()
                    .filter(candidate -> candidate.name().equals(TEST_APP_CONTAINER_NAME))
                    .findFirst()
                    .orElseThrow();
            int port = testAppContainer.getMappedPort(8080);
            assertThat(container.endpoints())
                    .containsExactly(new EndpointView(
                            "published-port:" + port,
                            "published-port",
                            "http://docker.example.com:" + port + "/",
                            true));
            ContainerView proxy = containers.stream()
                    .filter(candidate -> candidate.name().equals(TEST_PROXY_CONTAINER_NAME))
                    .findFirst()
                    .orElseThrow();
            assertThat(proxy.endpoints().getFirst().url()).isEqualTo("https://jellyfin.example.com/");
        }
    }
}
