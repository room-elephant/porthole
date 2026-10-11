package com.roomelephant.porthole.it.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.containers.GenericContainer;

@ResourceLock("native.agent.output.dir")
class PortholeContainerTest {
    @TempDir
    Path recordings;

    @Test
    void shouldGiveEveryAppItsOwnRecordingWithoutDeletingOtherLocks() throws Exception {
        String previous = System.getProperty("native.agent.output.dir");
        System.setProperty("native.agent.output.dir", recordings.toString());
        Path existingLock = Files.writeString(recordings.resolve(".lock"), "owned by another agent");
        try {
            PortholeContainer first = new PortholeContainer(1234);
            PortholeContainer second = new PortholeContainer(1234);
            String firstDirectory = first.getBinds().stream()
                    .filter(bind -> bind.getVolume().getPath().equals("/tmp/native-hints"))
                    .findFirst()
                    .orElseThrow()
                    .getPath();
            String secondDirectory = second.getBinds().stream()
                    .filter(bind -> bind.getVolume().getPath().equals("/tmp/native-hints"))
                    .findFirst()
                    .orElseThrow()
                    .getPath();
            assertThat(firstDirectory).isNotEqualTo(secondDirectory);
            assertThat(Path.of(firstDirectory)).startsWith(recordings).isDirectory();
            assertThat(first.getEnvMap().get("JAVA_TOOL_OPTIONS")).contains("native-image-agent");
            assertThat(Files.readString(existingLock)).isEqualTo("owned by another agent");
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void shouldRecordCustomSocketApplicationsToo() {
        String previous = System.getProperty("native.agent.output.dir");
        System.setProperty("native.agent.output.dir", recordings.toString());
        try {
            GenericContainer<?> app = PortholeContainer.withCustomSocket("/tmp/missing.sock", 1234);
            assertThat(app.getEnvMap().get("JAVA_TOOL_OPTIONS")).contains("native-image-agent");
            assertThat(app.getBinds()).hasSize(1);
            assertThat(app.getEnvMap()).containsEntry("PORTHOLE_DOCKER_HOST", "unix:///tmp/missing.sock");
        } finally {
            restoreProperty(previous);
        }
    }

    private void restoreProperty(String previous) {
        if (previous == null) System.clearProperty("native.agent.output.dir");
        else System.setProperty("native.agent.output.dir", previous);
    }
}
