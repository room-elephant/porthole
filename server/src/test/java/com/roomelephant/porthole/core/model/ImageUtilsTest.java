package com.roomelephant.porthole.core.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ImageUtils")
class ImageUtilsTest {

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
}
