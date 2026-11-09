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

    @Test
    @SuppressWarnings("unchecked")
    void publishedSpecListsThePatchFieldsAndTheirShapes() throws IOException {
        Map<String, Object> spec = new Yaml().load(Files.readString(Path.of("../docs/openapi.yml")));
        var paths = (Map<String, Map<String, Map<String, Object>>>) spec.get("paths");
        var body = (Map<String, Object>)
                paths.get("/api/entries/{id}").get("patch").get("requestBody");
        var content = (Map<String, Map<String, Object>>) body.get("content");
        assertThat(content.get("application/merge-patch+json").get("schema"))
                .isEqualTo(Map.of("$ref", "#/components/schemas/EntryPatch"));

        var schemas = (Map<String, Map<String, Object>>) ((Map<String, Object>) spec.get("components")).get("schemas");
        var fields =
                (Map<String, Map<String, Object>>) schemas.get("EntryPatch").get("properties");
        assertThat(fields).containsOnlyKeys("name", "stack", "icon", "endpoint");
        assertThat(String.valueOf(fields.get("icon"))).contains("#/components/schemas/IconChoice");
        assertThat(String.valueOf(schemas.get("IconChoice")))
                .contains("#/components/schemas/LibraryIcon", "#/components/schemas/UploadedIcon");
        assertThat(String.valueOf(fields.get("endpoint")))
                .contains("#/components/schemas/DiscoveredEndpoint", "#/components/schemas/CustomEndpoint")
                .contains("enum=[none]");

        var create = (Map<String, Map<String, Object>>)
                schemas.get("CreateLinkRequest").get("properties");
        assertThat(String.valueOf(create.get("icon"))).contains("#/components/schemas/IconChoice");
        assertThat(schemas).doesNotContainKey("JsonNode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishedSpecSaysWhichPatchResponsesHaveABody() throws IOException {
        Map<String, Object> spec = new Yaml().load(Files.readString(Path.of("../docs/openapi.yml")));
        var paths = (Map<String, Map<String, Map<String, Object>>>) spec.get("paths");
        var responses = (Map<String, Map<String, Object>>)
                paths.get("/api/entries/{id}").get("patch").get("responses");

        assertThat((Map<String, Object>) responses.get("200").get("content")).containsOnlyKeys("application/json");
        assertThat(responses.get("204")).doesNotContainKey("content");
    }
}
