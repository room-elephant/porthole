package com.roomelephant.porthole.outbound.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.roomelephant.porthole.config.properties.GhcrProperties;
import com.roomelephant.porthole.config.properties.RegistryProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("HttpRegistryOperations")
class RegistryServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RegistryProperties registryProperties;

    @Mock
    private RegistryProperties.Cache cache;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private RegistryProperties.DockerHub urls;

    private HttpRegistryOperations registryService;

    @BeforeEach
    void setUp() {
        when(registryProperties.cache()).thenReturn(cache);
        when(cache.ttl()).thenReturn(Duration.ofMinutes(5));
        when(cache.versionMaxSize()).thenReturn(100);

        registryService = new HttpRegistryOperations(restClient, registryProperties, ghcr());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "quay.io/acme/app:latest",
                "localhost:5000/app",
                "https://docker.io/nginx",
                "nginx@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            })
    void unsupportedReferencesNeverContactHub(String image) {
        var properties = new RegistryProperties(
                new RegistryProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(1)),
                new RegistryProperties.Cache(Duration.ofMinutes(5), 100),
                new RegistryProperties.DockerHub("https://registry/v2/", "https://auth/", "https://hub/"));
        var service = new HttpRegistryOperations(restClient, properties, ghcr());
        assertNull(service.getLatestVersion(image));
        assertNull(service.getDigest(image, "latest"));
        assertFalse(service.isTagChanged(image, java.util.List.of("sha256:" + "a".repeat(64))));
        verifyNoInteractions(restClient);
    }

    @Nested
    @DisplayName("getDigest")
    class GetDigest {

        @BeforeEach
        void setUpUrls() {
            when(registryProperties.dockerhub()).thenReturn(urls);
            when(urls.authUrl()).thenReturn("https://auth/");
        }

        @Test
        @DisplayName("should return null when auth token cannot be fetched")
        void shouldReturnNullWhenAuthTokenCannotBeFetched() {
            setupGetRequest();
            when(responseSpec.body(String.class)).thenReturn(null);

            String result = registryService.getDigest("nginx", "latest");

            assertNull(result);
        }

        @Test
        @DisplayName("should return digest when token and manifest are available")
        void shouldReturnDigestWhenTokenAndManifestAreAvailable() {
            setupGetRequest();
            when(urls.registryUrl()).thenReturn("https://registry/v2/");
            when(responseSpec.body(String.class)).thenReturn("{\"token\": \"test-token\"}");

            RestClient.RequestHeadersUriSpec headSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.RequestHeadersSpec headHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
            RestClient.ResponseSpec headResponseSpec = mock(RestClient.ResponseSpec.class);

            when(restClient.head()).thenReturn(headSpec);
            when(headSpec.uri(anyString())).thenReturn(headHeadersSpec);
            when(headHeadersSpec.header(anyString(), anyString())).thenReturn(headHeadersSpec);
            when(headHeadersSpec.retrieve()).thenReturn(headResponseSpec);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Docker-Content-Digest", "sha256:abc123");
            ResponseEntity<Void> entity = ResponseEntity.ok().headers(headers).build();
            when(headResponseSpec.toBodilessEntity()).thenReturn(entity);

            String result = registryService.getDigest("nginx", "latest");

            assertEquals("sha256:abc123", result);
        }

        @Test
        @DisplayName("should return null on exception")
        void shouldReturnNullOnException() {
            setupGetRequest();
            when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Network error"));

            String result = registryService.getDigest("nginx", "latest");

            assertNull(result);
        }

        @Test
        @DisplayName("should handle image with namespace")
        void shouldHandleImageWithNamespace() {
            setupGetRequest();
            when(responseSpec.body(String.class)).thenReturn(null);

            String result = registryService.getDigest("bitnami/redis:7", "7");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("getLatestVersion")
    class GetLatestVersion {

        @BeforeEach
        void setUpUrls() {
            when(registryProperties.timeout())
                    .thenReturn(new RegistryProperties.Timeout(Duration.ofSeconds(1), Duration.ofSeconds(10)));
            when(registryProperties.dockerhub()).thenReturn(urls);
            when(urls.repositoriesUrl()).thenReturn("https://repositories/");
        }

        @Test
        void invalidationFetchesNewTagsBeforeCacheExpiry() {
            setupGetRequest();
            when(responseSpec.body(String.class))
                    .thenReturn("{\"results\":[{\"name\":\"2.0\"}]}", "{\"results\":[{\"name\":\"3.0\"}]}");
            assertEquals("2.0", registryService.getLatestVersion("nginx"));
            assertEquals("2.0", registryService.getLatestVersion("nginx"));
            registryService.invalidateVersionCache();
            assertEquals("3.0", registryService.getLatestVersion("nginx"));
        }

        @Test
        @DisplayName("should return null when hub returns 404")
        void shouldReturnNullWhenHubReturns404() {
            setupGetRequest();
            when(responseSpec.body(String.class))
                    .thenThrow(org.springframework.web.client.HttpClientErrorException.create(
                            org.springframework.http.HttpStatus.NOT_FOUND,
                            "Not Found",
                            org.springframework.http.HttpHeaders.EMPTY,
                            null,
                            null));

            String result = registryService.getLatestVersion("nginx");

            assertNull(result);
        }

        @Test
        @DisplayName("should propagate hub request failures")
        void shouldPropagateHubRequestFailure() {
            setupGetRequest();
            when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Network error"));

            assertThrows(RuntimeException.class, () -> registryService.getLatestVersion("nginx"));
        }

        @Test
        @DisplayName("should reject missing response bodies")
        void shouldRejectMissingResponseBody() {
            setupGetRequest();
            when(responseSpec.body(String.class)).thenReturn(null);

            assertThrows(RuntimeException.class, () -> registryService.getLatestVersion("nginx"));
        }

        @Test
        @DisplayName("should reject missing tag results")
        void shouldRejectMissingTagResults() {
            setupGetRequest();
            when(responseSpec.body(String.class)).thenReturn("{\"count\": 0}");

            assertThrows(RuntimeException.class, () -> registryService.getLatestVersion("nginx"));
        }

        @Test
        @DisplayName("should return latest semver version from tags")
        void shouldReturnLatestSemverVersionFromTags() {
            setupGetRequest();
            String tagsResponse = """
                    {
                        "results": [
                            {"name": "latest"},
                            {"name": "1.24"},
                            {"name": "1.25"},
                            {"name": "1.25.1"},
                            {"name": "alpine"}
                        ]
                    }
                    """;
            when(responseSpec.body(String.class)).thenReturn(tagsResponse);

            String result = registryService.getLatestVersion("nginx");

            assertEquals("1.25.1", result);
        }

        @Test
        @DisplayName("should return null when no semver tags found")
        void shouldReturnNullWhenNoSemverTagsFound() {
            setupGetRequest();
            String tagsResponse = """
                    {
                        "results": [
                            {"name": "latest"},
                            {"name": "alpine"},
                            {"name": "bookworm"}
                        ]
                    }
                    """;
            when(responseSpec.body(String.class)).thenReturn(tagsResponse);

            String result = registryService.getLatestVersion("nginx");

            assertNull(result);
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(
                strings = {"https://repositories/library/nginx/tags?page=2", "?page=2", "/library/nginx/tags?page=2"})
        void readsAllPagesBeforeSelectingTheLatestVersion(String next) {
            setupGetRequest();
            when(responseSpec.body(String.class))
                    .thenReturn(
                            "{\"results\":[{\"name\":\"1.0\"}],\"next\":\"" + next + "\"}",
                            "{\"results\":[{\"name\":\"2.0\"}],\"next\":null}");
            assertEquals("2.0", registryService.getLatestVersion("nginx:1.0"));
            verify(restClient, times(2)).get();
            verify(requestHeadersSpec, times(2)).retrieve();
        }

        @Test
        void pageLimitDoesNotReturnAPartialMaximum() {
            setupGetRequest();
            var page = new java.util.concurrent.atomic.AtomicInteger(1);
            when(responseSpec.body(String.class))
                    .thenAnswer(invocation ->
                            "{\"results\":[{\"name\":\"9.0\"}],\"next\":\"?page=" + page.incrementAndGet() + "\"}");
            assertThrows(RuntimeException.class, () -> registryService.getLatestVersion("nginx"));
            verify(restClient, times(100)).get();
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(
                strings = {
                    "https://evil.example/tags",
                    "https://repositories/library/other/tags",
                    "https://repositories/library/nginx/tags?page_size=100"
                })
        void incompleteOrUnsafeCatalogueHasNoLatestVersion(String next) {
            setupGetRequest();
            when(responseSpec.body(String.class))
                    .thenReturn("{\"results\":[{\"name\":\"9.0\"}],\"next\":\"" + next + "\"}");
            assertThrows(RuntimeException.class, () -> registryService.getLatestVersion("nginx"));
            verify(restClient).get();
        }

        @Test
        void laterPageFailureDoesNotReturnAPartialMaximum() {
            setupGetRequest();
            when(responseSpec.body(String.class))
                    .thenReturn("{\"results\":[{\"name\":\"9.0\"}],\"next\":\"?page=2\"}")
                    .thenThrow(new RuntimeException("rate limited"));
            assertThrows(RuntimeException.class, () -> registryService.getLatestVersion("nginx"));
            verify(restClient, times(2)).get();
        }

        @Test
        void ignoresMalformedTagsAndSharesCatalogueAcrossHubAliases() {
            setupGetRequest();
            when(responseSpec.body(String.class))
                    .thenReturn(
                            "{\"results\":[{}, {\"name\":null},{\"name\":42},{\"name\":\"1.0-alpine\"},{\"name\":\"2147483648.0\"}]}");
            assertEquals("2147483648.0", registryService.getLatestVersion("nginx:1.0"));
            assertEquals("2147483648.0", registryService.getLatestVersion("index.docker.io/library/nginx:2.0"));
            verify(restClient).get();
        }

        @Test
        @DisplayName("should cache version results")
        void shouldCacheVersionResults() {
            setupGetRequest();
            String tagsResponse = """
                    {
                        "results": [
                            {"name": "1.0"}
                        ]
                    }
                    """;
            when(responseSpec.body(String.class)).thenReturn(tagsResponse);

            String result1 = registryService.getLatestVersion("nginx");
            String result2 = registryService.getLatestVersion("nginx");

            assertEquals("1.0", result1);
            assertEquals("1.0", result2);

            verify(restClient, times(1)).get();
        }
    }

    @SuppressWarnings("unchecked")
    private void setupGetRequest() {
        RestClient.RequestHeadersUriSpec getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        when(restClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    private static GhcrProperties ghcr() {
        return new GhcrProperties("https://ghcr.io/v2/", "https://ghcr.io/token?service=ghcr.io&scope=repository:");
    }
}
