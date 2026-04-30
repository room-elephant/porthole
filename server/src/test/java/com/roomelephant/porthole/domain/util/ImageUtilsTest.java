package com.roomelephant.porthole.domain.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ImageUtils")
class ImageUtilsTest {

    @Nested
    @DisplayName("extractTag")
    class ExtractTag {

        @Test
        @DisplayName("should extract tag from image with tag")
        void shouldExtractTagFromImageWithTag() {
            assertEquals("1.25", ImageUtils.extractTag("nginx:1.25"));
        }

        @Test
        @DisplayName("should return 'latest' for image without tag")
        void shouldReturnLatestForImageWithoutTag() {
            assertEquals("latest", ImageUtils.extractTag("nginx"));
        }

        @Test
        @DisplayName("should extract tag from image with registry prefix")
        void shouldExtractTagFromImageWithRegistryPrefix() {
            assertEquals("15", ImageUtils.extractTag("my-registry.io/postgres:15"));
        }

        @Test
        @DisplayName("should handle complex tags")
        void shouldHandleComplexTags() {
            assertEquals("alpine3.18", ImageUtils.extractTag("nginx:alpine3.18"));
        }
    }

    @Nested
    @DisplayName("extractName")
    class ExtractName {

        @Test
        @DisplayName("should extract name from simple image")
        void shouldExtractNameFromSimpleImage() {
            assertEquals("nginx", ImageUtils.extractName("nginx"));
        }

        @Test
        @DisplayName("should extract name from image with tag")
        void shouldExtractNameFromImageWithTag() {
            assertEquals("postgres", ImageUtils.extractName("postgres:15"));
        }

        @Test
        @DisplayName("should extract name from image with registry prefix")
        void shouldExtractNameFromImageWithRegistryPrefix() {
            assertEquals("redis", ImageUtils.extractName("bitnami/redis:7"));
        }

        @Test
        @DisplayName("should extract name from image with full registry path")
        void shouldExtractNameFromImageWithFullRegistryPath() {
            assertEquals("myapp", ImageUtils.extractName("ghcr.io/myorg/myapp:v1.0"));
        }

        @Test
        @DisplayName("should handle image without tag but with registry")
        void shouldHandleImageWithoutTagButWithRegistry() {
            assertEquals("myservice", ImageUtils.extractName("docker.io/library/myservice"));
        }
    }

    @Nested
    @DisplayName("resolveRepository")
    class ResolveRepository {

        @Test
        @DisplayName("should add library prefix for official images")
        void shouldAddLibraryPrefixForOfficialImages() {
            assertEquals("library/redis", ImageUtils.resolveRepository("redis"));
        }

        @Test
        @DisplayName("should not add library prefix for images with namespace")
        void shouldNotAddLibraryPrefixForImagesWithNamespace() {
            assertEquals("bitnami/redis", ImageUtils.resolveRepository("bitnami/redis:7"));
        }

        @Test
        @DisplayName("should strip tag from repository")
        void shouldStripTagFromRepository() {
            assertEquals("library/nginx", ImageUtils.resolveRepository("nginx:1.25"));
        }

        @Test
        @DisplayName("should handle image without tag")
        void shouldHandleImageWithoutTag() {
            assertEquals("myorg/myapp", ImageUtils.resolveRepository("myorg/myapp"));
        }
    }

    @Nested
    @DisplayName("isSemver")
    class IsSemver {

        @ParameterizedTest
        @ValueSource(strings = {"1.0", "1.0.0", "2.3.4", "v1.0", "v2.3.4", "10.20.30"})
        @DisplayName("should return true for valid semver tags")
        void shouldReturnTrueForValidSemverTags(String tag) {
            assertTrue(ImageUtils.isSemver(tag));
        }

        @ParameterizedTest
        @ValueSource(strings = {"latest", "alpine", "alpine3.18", "v", "1.0-beta", "1.0.0-rc1"})
        @DisplayName("should return false for non-semver tags")
        void shouldReturnFalseForNonSemverTags(String tag) {
            assertFalse(ImageUtils.isSemver(tag));
        }
    }

    @Nested
    @DisplayName("compareSemVer")
    class CompareSemVer {

        @ParameterizedTest
        @CsvSource({
            "1.0, 2.0, -1",
            "2.0, 1.0, 1",
            "1.0, 1.0, 0",
            "1.0.0, 1.0.1, -1",
            "1.0.1, 1.0.0, 1",
            "1.2.3, 1.2.3, 0",
            "v1.0, v2.0, -1",
            "v1.0, 1.0, 0",
            "1.0, 1.0.0, 0",
            "2.0, 1.9.9, 1"
        })
        @DisplayName("should compare versions correctly")
        void shouldCompareVersionsCorrectly(String v1, String v2, int expectedSign) {
            int result = ImageUtils.compareSemVer(v1, v2);
            if (expectedSign < 0) {
                assertTrue(result < 0, "Expected " + v1 + " < " + v2);
            } else if (expectedSign > 0) {
                assertTrue(result > 0, "Expected " + v1 + " > " + v2);
            } else {
                assertEquals(0, result, "Expected " + v1 + " == " + v2);
            }
        }

        @Test
        @DisplayName("should handle versions with different number of parts")
        void shouldHandleVersionsWithDifferentNumberOfParts() {
            assertTrue(ImageUtils.compareSemVer("1.0.0.1", "1.0.0") > 0);
            assertTrue(ImageUtils.compareSemVer("1.0", "1.0.0.1") < 0);
        }
    }
}
