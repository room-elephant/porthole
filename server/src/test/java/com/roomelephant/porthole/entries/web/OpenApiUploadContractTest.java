package com.roomelephant.porthole.entries.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiUploadContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void publishedSpecDescribesUploadAsARawBinaryBody() throws IOException {
        Map<String, Object> spec = new Yaml().load(Files.readString(Path.of("../docs/openapi.yml")));
        var paths = (Map<String, Map<String, Map<String, Object>>>) spec.get("paths");
        Map<String, Object> upload = paths.get("/api/icons").get("post");
        var content =
                (Map<String, Map<String, Object>>) ((Map<String, Object>) upload.get("requestBody")).get("content");

        assertThat(content).containsOnlyKeys("application/octet-stream");
        assertThat(upload).doesNotContainKey("parameters");
        assertThat(content.get("application/octet-stream").get("schema"))
                .isEqualTo(Map.of("type", "string", "format", "binary"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishedSpecDescribesPatchAsMergePatch() throws IOException {
        Map<String, Object> spec = new Yaml().load(Files.readString(Path.of("../docs/openapi.yml")));
        var paths = (Map<String, Map<String, Map<String, Object>>>) spec.get("paths");
        Map<String, Object> patch = paths.get("/api/entries/{id}").get("patch");

        assertThat((Map<String, Object>) ((Map<String, Object>) patch.get("requestBody")).get("content"))
                .containsKey("application/merge-patch+json");
    }
}
