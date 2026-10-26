package com.roomelephant.porthole.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.ImageReference;
import com.roomelephant.porthole.core.model.RegistryLookup;
import com.roomelephant.porthole.core.model.TagCatalogue;
import com.roomelephant.porthole.outbound.registry.RegistryClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RegistryConfigTest {
    @ParameterizedTest
    @ValueSource(strings = {"dockerHubClient", "ghcrClient"})
    void registryClientsHonorSpringJacksonConfiguration(String beanName) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var http = builder.build();
        boolean hub = beanName.equals("dockerHubClient");
        if (hub) {
            server.expect(requestTo("https://catalog/library/nginx/tags?page_size=100"))
                    .andRespond(withSuccess("{'results':[{'name':'1.0'}]}", MediaType.APPLICATION_JSON));
        } else {
            server.expect(requestTo("https://auth/acme/app:pull"))
                    .andRespond(withSuccess("{'token':'anonymous'}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://registry/acme/app/tags/list?n=100"))
                    .andRespond(withSuccess("{'tags':['1.0']}", MediaType.APPLICATION_JSON));
        }

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(RegistryConfig.class)
                .withBean(
                        RegistryProperties.class,
                        () -> new RegistryProperties(
                                new RegistryProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(1)),
                                new RegistryProperties.Cache(Duration.ofMinutes(1), 100),
                                new RegistryProperties.DockerHub(
                                        "https://registry/", "https://auth/", "https://catalog/"),
                                new RegistryProperties.Ghcr("https://registry/", "https://auth/")))
                .withBean("testRestClient", RestClient.class, () -> http, definition -> definition.setPrimary(true))
                .withPropertyValues("spring.jackson.json.read.allow-single-quotes=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var image = ImageReference.tryParse(hub ? "nginx:latest" : "ghcr.io/acme/app:latest")
                            .orElseThrow();
                    assertThat(context.getBean(beanName, RegistryClient.class).listTags(image))
                            .isEqualTo(new RegistryLookup.Available<>(new TagCatalogue(List.of("1.0"))));
                });
        server.verify();
    }
}
