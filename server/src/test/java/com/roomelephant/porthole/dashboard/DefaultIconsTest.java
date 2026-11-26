package com.roomelephant.porthole.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DefaultIconsTest {
    private final DefaultIcons icons = new DefaultIcons(Map.of("postgres", "postgresql", "team_app", "custom-icon"));

    @ParameterizedTest
    @CsvSource({
        "postgres:16, postgresql",
        "nginx, nginx",
        "docker.io/library/postgres, postgresql",
        "my_custom_app:1, my-custom-app",
        "registry.example.com/team/team_app:2, custom-icon",
        "ghcr.io/org/my-app@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef, my-app",
        "Not A Valid Reference, docker",
        "'', docker",
        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef, docker"
    })
    void namesTheLibraryIconForAnImage(String image, String icon) {
        assertThat(icons.nameFor(image)).isEqualTo(icon);
    }
}
