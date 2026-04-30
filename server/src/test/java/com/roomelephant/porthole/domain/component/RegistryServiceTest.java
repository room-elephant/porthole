package com.roomelephant.porthole.domain.component;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
@DisplayName("RegistryService")
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
    private RegistryProperties.Urls urls;

    private RegistryService registryService;

    @BeforeEach
    void setUp() {
        when(registryProperties.cache()).thenReturn(cache);
        when(cache.ttl()).thenReturn(Duration.ofMinutes(5));
        when(cache.versionMaxSize()).thenReturn(100);
        when(registryProperties.urls()).thenReturn(urls);

        registryService = new RegistryService(restClient, registryProperties);
    }

    @Nested
    @DisplayName("getDigest")
    class GetDigest {

        @BeforeEach
        void setUpUrls() {
            when(urls.auth()).thenReturn("https://auth/");
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
            when(urls.registry()).thenReturn("https://registry/v2/");
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

            String result = registryService.getDigest("bitnami/redis", "7");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("getLatestVersion")
    class GetLatestVersion {

        @BeforeEach
        void setUpUrls() {
            when(urls.repositories()).thenReturn("https://repositories/");
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
        @DisplayName("should return null when hub request fails")
        void shouldReturnNullWhenHubRequestFails() {
            setupGetRequest();
            when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Network error"));

            String result = registryService.getLatestVersion("nginx");

            assertNull(result);
        }

        @Test
        @DisplayName("should return null when response body is null")
        void shouldReturnNullWhenResponseBodyIsNull() {
            setupGetRequest();
            when(responseSpec.body(String.class)).thenReturn(null);

            String result = registryService.getLatestVersion("nginx");

            assertNull(result);
        }

        @Test
        @DisplayName("should return null when no results in response")
        void shouldReturnNullWhenNoResultsInResponse() {
            setupGetRequest();
            when(responseSpec.body(String.class)).thenReturn("{\"count\": 0}");

            String result = registryService.getLatestVersion("nginx");

            assertNull(result);
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
}
