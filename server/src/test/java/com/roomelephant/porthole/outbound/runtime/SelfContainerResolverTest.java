package com.roomelephant.porthole.outbound.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelfContainerResolverTest {
    private static final String MOUNT_ID = "1".repeat(64);
    private static final String CGROUP_ID = "2".repeat(64);
    private static final String HOSTNAME = "3a3a3a3a3a3a";

    @TempDir
    Path dir;

    private Path mountinfo() throws IOException {
        return Files.writeString(dir.resolve("mountinfo"), """
                600 590 0:52 / / rw,relatime master:1 - overlay overlay rw
                612 600 254:1 /docker/containers/%s/hostname /etc/hostname rw,relatime - ext4 /dev/vda1 rw
                """.formatted(MOUNT_ID));
    }

    private static final String OTHER_ID = "4".repeat(64);
    private static final String DECOY =
            "620 600 0:60 /docker/containers/%s/mounts/shm /dev/shm rw,nosuid - tmpfs shm rw%n".formatted(OTHER_ID);

    private Path cgroup() throws IOException {
        return Files.writeString(dir.resolve("cgroup"), "0::/system.slice/docker-" + CGROUP_ID + ".scope\n");
    }

    private Path missing(String name) {
        return dir.resolve(name);
    }

    private SelfContainerResolver resolver(Map<String, String> env, Path mountinfo, Path cgroup) {
        return new SelfContainerResolver(env::get, mountinfo, cgroup);
    }

    @Test
    void overrideWinsTrimmedAndLowercased() throws IOException {
        var self = resolver(Map.of("HOSTNAME", HOSTNAME), mountinfo(), cgroup()).resolve("  ABCDEF123456 ");
        assertThat(self.id()).isEqualTo("abcdef123456");
    }

    @Test
    void blankOverrideIsIgnored() throws IOException {
        assertThat(resolver(Map.of(), mountinfo(), cgroup()).resolve("  ").id()).isEqualTo(MOUNT_ID);
    }

    @Test
    void mountinfoFullIdBeatsHostname() throws IOException {
        var self = resolver(Map.of("HOSTNAME", HOSTNAME), mountinfo(), cgroup()).resolve(null);
        assertThat(self.id()).isEqualTo(MOUNT_ID);
    }

    @Test
    void cgroupIsUsedWhenMountinfoHasNoId() throws IOException {
        Path mountinfo = Files.writeString(dir.resolve("mountinfo"), "600 590 0:52 / / rw - overlay overlay rw\n");
        var self = resolver(Map.of("HOSTNAME", HOSTNAME), mountinfo, cgroup()).resolve(null);
        assertThat(self.id()).isEqualTo(CGROUP_ID);
    }

    @Test
    void hexHostnameIsUsedWhenNoFiles() {
        var self = resolver(Map.of("HOSTNAME", HOSTNAME), missing("m"), missing("c"))
                .resolve(null);
        assertThat(self.id()).isEqualTo(HOSTNAME);
    }

    @Test
    void nonHexHostnameWithoutFilesResolvesToNone() {
        var self = resolver(Map.of("HOSTNAME", "my-laptop"), missing("m"), missing("c"))
                .resolve(null);
        assertThat(self.id()).isNull();
    }

    @Test
    void missingFilesAndEnvironmentAreTolerated() {
        assertThat(resolver(Map.of(), missing("m"), missing("c")).resolve(null).id())
                .isNull();
    }

    @Test
    void unreadableFileIsSkipped() throws IOException {
        Path directory = Files.createDirectory(dir.resolve("not-a-file"));
        assertThat(resolver(Map.of(), directory, cgroup()).resolve(null).id()).isEqualTo(CGROUP_ID);
    }

    @Test
    void decoyMountBeforeRealHostnameMountIsIgnored() throws IOException {
        Path mountinfo = Files.writeString(
                dir.resolve("mountinfo"),
                "600 590 0:52 / / rw - overlay overlay rw\n"
                        + DECOY
                        + "612 600 254:1 /docker/containers/%s/hostname /etc/hostname rw - ext4 /dev/vda1 rw%n"
                                .formatted(MOUNT_ID));
        assertThat(resolver(Map.of(), mountinfo, cgroup()).resolve(null).id()).isEqualTo(MOUNT_ID);
    }

    @Test
    void decoyOnlyMountinfoFallsThroughToCgroup() throws IOException {
        Path mountinfo = Files.writeString(dir.resolve("mountinfo"), DECOY);
        assertThat(resolver(Map.of(), mountinfo, cgroup()).resolve(null).id()).isEqualTo(CGROUP_ID);
    }

    @Test
    void decoyOnlyMountinfoWithNothingElseResolvesToNone() throws IOException {
        Path mountinfo = Files.writeString(dir.resolve("mountinfo"), DECOY);
        assertThat(resolver(Map.of(), mountinfo, missing("c")).resolve(null).id())
                .isNull();
    }
}
