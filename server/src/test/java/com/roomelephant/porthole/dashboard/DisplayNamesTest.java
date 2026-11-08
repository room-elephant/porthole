package com.roomelephant.porthole.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.fleet.ContainerSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DisplayNamesTest {
    private final DisplayNames names = new DisplayNames();

    @Test
    void resolvesNamesFromTheWholeSource() {
        List<ContainerSnapshot> containers = List.of(
                container("project-web-1", "project"),
                container("project-web-2", "project"),
                container("project-web-1-1", "project"),
                container("project-api-1", "project"),
                container("other-api-1", "other"),
                container("standalone-1", null),
                container("custom-1", "project"),
                container("project-worker", "project"));

        assertThat(names.resolve(containers))
                .containsExactly("web-1", "web-2", "web-1-1", "api", "api", "standalone-1", "custom-1", "worker");
    }

    @Test
    void keepsReplicaSuffixWhenItCollidesWithAnUnsuffixedName() {
        assertThat(names.resolve(List.of(container("project-web", "project"), container("project-web-1", "project"))))
                .containsExactly("web", "web-1");
    }

    @Test
    void acceptsEmptySource() {
        assertThat(names.resolve(List.of())).isEmpty();
    }

    @Test
    void consecutiveCallsDoNotShareCollisionState() {
        assertThat(names.resolve(List.of(container("project-web-1", "project"), container("project-web-2", "project"))))
                .containsExactly("web-1", "web-2");
        assertThat(names.resolve(List.of(container("project-web-1", "project"))))
                .containsExactly("web");
    }

    static ContainerSnapshot container(String name, @Nullable String project) {
        return new ContainerSnapshot(
                name,
                name,
                "nginx",
                "image-" + name,
                Set.of(),
                project == null ? Map.of() : Map.of("com.docker.compose.project", project),
                "running",
                "Up");
    }
}
