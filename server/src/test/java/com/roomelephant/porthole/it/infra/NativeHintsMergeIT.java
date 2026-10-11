package com.roomelephant.porthole.it.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NativeHintsMergeIT {
    @TempDir
    Path temporary;

    @Test
    void shouldKeepPublishedMetadataWhenGraalVmRejectsARecording() throws Exception {
        Path recordings = Files.createDirectory(temporary.resolve("recordings"));
        Path app = Files.createDirectory(recordings.resolve("app-invalid"));
        Files.writeString(app.resolve("reachability-metadata.json"), "invalid JSON");
        Path output = Files.createDirectory(temporary.resolve("output"));
        Path published = Files.writeString(output.resolve("reachability-metadata.json"), "published");

        assertThatThrownBy(() -> NativeHintsMerger.merge(recordings, output))
                .isInstanceOf(org.testcontainers.containers.ContainerLaunchException.class);
        assertThat(Files.readString(published)).isEqualTo("published");
    }

    @Test
    void shouldMergeTheUnionOfIndependentRecordingsUsingGraalVm() throws Exception {
        Path recordings = Files.createDirectory(temporary.resolve("recordings"));
        Path first = Files.createDirectory(recordings.resolve("app-first"));
        Path second = Files.createDirectory(recordings.resolve("app-second"));
        Files.writeString(
                first.resolve("reachability-metadata.json"), "{\"reflection\":[{\"type\":\"java.lang.String\"}]}");
        Files.writeString(
                second.resolve("reachability-metadata.json"),
                "{\"reflection\":[{\"type\":\"java.lang.Integer\"},{\"type\":\"java.lang.String\"}]}");
        Path output = Files.createDirectory(temporary.resolve("output"));
        Files.writeString(output.resolve("reachability-metadata.json"), "{\"reflection\":[]}");

        NativeHintsMerger.merge(recordings, output);

        JsonNode merged = JsonMapper.builder()
                .build()
                .readTree(output.resolve("reachability-metadata.json").toFile());
        List<String> types = new ArrayList<>();
        merged.get("reflection").forEach(entry -> types.add(entry.get("type").asString()));
        assertThat(types).containsExactlyInAnyOrder("java.lang.String", "java.lang.Integer");
    }
}
