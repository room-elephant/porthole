package com.roomelephant.porthole.inbound.web;

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
    void publishedSpecDescribesUploadAsMultipartFormFields() throws IOException {
        Map<String, Object> spec = new Yaml().load(Files.readString(Path.of("../docs/openapi.yml")));
        var paths = (Map<String, Map<String, Map<String, Object>>>) spec.get("paths");
        Map<String, Object> upload = paths.get("/api/containers/icon/upload").get("post");
        var content =
                (Map<String, Map<String, Object>>) ((Map<String, Object>) upload.get("requestBody")).get("content");

        assertThat(content).containsOnlyKeys("multipart/form-data");
        assertThat(upload).doesNotContainKey("parameters");
        assertThat(content.get("multipart/form-data").get("schema"))
                .isEqualTo(Map.of("$ref", "#/components/schemas/UploadContainerIconForm"));
        var schemas = (Map<String, Map<String, Object>>) ((Map<String, Object>) spec.get("components")).get("schemas");
        assertThat((Map<String, Object>) schemas.get("UploadContainerIconForm").get("properties"))
                .containsOnlyKeys("sourceId", "containerName", "file");
    }
}
