package com.roomelephant.porthole.it.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.it.infra.ContainerAwareIntegrationTestBase;
import com.roomelephant.porthole.it.infra.DashboardView;
import com.roomelephant.porthole.it.infra.DashboardView.ContainerView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DashboardEndpointIT extends ContainerAwareIntegrationTestBase {
    private final RestTemplate http = new RestTemplate(new JdkClientHttpRequestFactory());
    private final JsonMapper mapper = JsonMapper.builder().build();

    private String url(String path) {
        startPorthole();
        return porthole.baseUrl() + path;
    }

    private static HttpEntity<String> json(String body, String type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(type));
        return new HttpEntity<>(body, headers);
    }

    @Test
    void dashboardListsContainersAndLinks() {
        String request = "{\"name\":\"Router\",\"url\":\"http://192.168.50.1/\",\"stack\":\"base\"}";
        ResponseEntity<String> created =
                http.postForEntity(url("/api/entries"), json(request, "application/json"), String.class);

        DashboardView dashboard = dashboard();

        assertThat(dashboard.sources())
                .extracting(DashboardView.SourceView::status)
                .containsExactly("UP");
        assertThat(dashboard.containers())
                .extracting(ContainerView::name)
                .contains(TEST_APP_CONTAINER_NAME, TEST_STOPPED_CONTAINER_NAME, TEST_NO_PORTS_CONTAINER_NAME);
        assertThat(dashboard.links()).singleElement().satisfies(link -> {
            assertThat(link.id())
                    .isEqualTo(mapper.readTree(created.getBody()).path("id").asString());
            assertThat(link.url()).isEqualTo("http://192.168.50.1/");
            assertThat(link.stack()).isEqualTo("base");
        });
        http.delete(url("/api/entries/" + dashboard.links().getFirst().id()));
    }

    @Test
    void postReturnsTheResolvedLink() {
        String request = "{\"name\":\"NAS\",\"url\":\"http://nas/\",\"icon\":{\"library\":\"synology\"}}";

        ResponseEntity<String> response =
                http.postForEntity(url("/api/entries"), json(request, "application/json"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode link = mapper.readTree(response.getBody());
        assertThat(link.path("kind").asString()).isEqualTo("link");
        assertThat(link.path("icon").path("url").asString()).endsWith("/synology.webp");
        assertThat(link.path("icon").path("choice").path("library").asString()).isEqualTo("synology");
        assertThat(link.path("endpoints").get(0).path("url").asString()).isEqualTo("http://nas/");
        http.delete(url("/api/entries/" + link.path("id").asString()));
    }

    @Test
    void patchReturnsResolvedEntry() {
        String id = "container:local:" + TEST_APP_CONTAINER_NAME;

        ResponseEntity<String> response = http.exchange(
                url("/api/entries/" + id),
                HttpMethod.PATCH,
                json("{\"name\":\"Renamed app\"}", "application/merge-patch+json"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode entry = mapper.readTree(response.getBody());
        assertThat(entry.path("id").asString()).isEqualTo(id);
        assertThat(entry.path("name").asString()).isEqualTo("Renamed app");
        assertThat(entry.path("container").path("defaultName").asString()).isEqualTo(TEST_APP_CONTAINER_NAME);
        assertThat(dashboard().containers())
                .filteredOn(container -> container.name().equals(TEST_APP_CONTAINER_NAME))
                .singleElement()
                .satisfies(container -> assertThat(container.displayName()).isEqualTo("Renamed app"));
        http.exchange(
                url("/api/entries/" + id),
                HttpMethod.PATCH,
                json("{\"name\":null}", "application/merge-patch+json"),
                String.class);
    }

    @Test
    void patchOfAContainerNoSourceListsIs204() {
        ResponseEntity<String> response = http.exchange(
                url("/api/entries/container:local:not-running-yet"),
                HttpMethod.PATCH,
                json("{\"name\":\"Later\"}", "application/merge-patch+json"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        http.exchange(
                url("/api/entries/container:local:not-running-yet"),
                HttpMethod.PATCH,
                json("{\"name\":null}", "application/merge-patch+json"),
                String.class);
    }

    @Test
    void removedEndpointsAre404() {
        assertThat(fetch("/api/containers").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fetch("/api/links").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
