package com.roomelephant.porthole.versions.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.roomelephant.porthole.shared.ImageReference;
import com.roomelephant.porthole.versions.RegistryLookup;
import com.roomelephant.porthole.versions.TagCatalogue;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RegistryConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"dockerHubClient", "ghcrClient"})
    void registryClientsHonorSpringJacksonConfiguration(String beanName) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var http = builder.build();
        boolean hub = beanName.equals("dockerHubClient");
        if (hub) {
            server.expect(requestTo("https://auth/library/nginx:pull"))
                    .andRespond(withSuccess("{'token':'anonymous'}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://registry/library/nginx/tags/list?n=1000"))
                    .andRespond(withSuccess("{'tags':['1.0']}", MediaType.APPLICATION_JSON));
        } else {
            server.expect(requestTo("https://auth/acme/app:pull"))
                    .andRespond(withSuccess("{'token':'anonymous'}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://registry/acme/app/tags/list?n=100"))
                    .andRespond(withSuccess("{'tags':['1.0']}", MediaType.APPLICATION_JSON));
        }

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(RegistryConfiguration.class)
                .withBean("testRestClient", RestClient.class, () -> http, definition -> definition.setPrimary(true))
                .withPropertyValues(
                        "spring.jackson.json.read.allow-single-quotes=true",
                        "porthole.registry.timeout.connect=1s",
                        "porthole.registry.timeout.read=1s",
                        "porthole.registry.cache.ttl=1m",
                        "porthole.registry.cache.version-max-size=100",
                        "porthole.registry.dockerhub.registry-url=https://registry/",
                        "porthole.registry.dockerhub.auth-url=https://auth/",
                        "porthole.registry.ghcr.registry-url=https://registry/",
                        "porthole.registry.ghcr.auth-url=https://auth/")
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
