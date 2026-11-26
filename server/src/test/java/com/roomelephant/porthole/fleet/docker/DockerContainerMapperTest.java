package com.roomelephant.porthole.fleet.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.roomelephant.porthole.fleet.ContainerSnapshot;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DockerContainerMapperTest {
    private final DockerContainerMapper mapper = new DockerContainerMapper();

    @Test
    void mapsAListedContainer() {
        Container raw = mock(Container.class);
        when(raw.getId()).thenReturn("abc");
        when(raw.getNames()).thenReturn(new String[] {"/web", "/alias"});
        when(raw.getImage()).thenReturn("nginx:1");
        when(raw.getImageId()).thenReturn("sha256:a");
        when(raw.getPorts())
                .thenReturn(new ContainerPort[] {new ContainerPort().withPublicPort(8080), new ContainerPort()});
        when(raw.getLabels()).thenReturn(Map.of("app", "web"));
        when(raw.getState()).thenReturn("running");
        when(raw.getStatus()).thenReturn("Up 2 hours");

        assertThat(mapper.toSnapshot(raw))
                .isEqualTo(new ContainerSnapshot(
                        "abc",
                        "web",
                        "nginx:1",
                        "sha256:a",
                        Set.of(8080),
                        Map.of("app", "web"),
                        "running",
                        "Up 2 hours"));
    }

    @Test
    void fillsInWhatDockerLeavesOut() {
        Container noNames = mock(Container.class);
        Container emptyNames = mock(Container.class);
        when(emptyNames.getNames()).thenReturn(new String[0]);

        for (Container raw : new Container[] {noNames, emptyNames}) {
            ContainerSnapshot snapshot = mapper.toSnapshot(raw);
            assertThat(snapshot.name()).isEqualTo("Unknown");
            assertThat(snapshot.publishedPorts()).isEmpty();
            assertThat(snapshot.labels()).isEmpty();
        }
    }
}
