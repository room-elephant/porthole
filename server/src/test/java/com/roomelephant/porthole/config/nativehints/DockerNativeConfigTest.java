package com.roomelephant.porthole.config.nativehints;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerConfigFile;
import com.github.dockerjava.core.DockerContextMetaFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

@DisplayName("DockerNativeConfig")
class DockerNativeConfigTest {

    private RuntimeHints hints;

    @BeforeEach
    void setUp() {
        hints = new RuntimeHints();
        new DockerNativeConfig().registerHints(hints, null);
    }

    @Test
    @DisplayName("should register api.model classes for reflection")
    void shouldRegisterApiModelClassesForReflection() {
        assertThat(RuntimeHintsPredicates.reflection().onType(Container.class)).accepts(hints);
    }

    @Test
    @DisplayName("should register api.command classes for reflection")
    void shouldRegisterApiCommandClassesForReflection() {
        assertThat(RuntimeHintsPredicates.reflection().onType(CreateContainerResponse.class))
                .accepts(hints);
    }

    @Test
    @DisplayName("should register DockerConfigFile for reflection")
    void shouldRegisterDockerConfigFileForReflection() {
        assertThat(RuntimeHintsPredicates.reflection().onType(DockerConfigFile.class))
                .accepts(hints);
    }

    @Test
    @DisplayName("should register DockerContextMetaFile for reflection")
    void shouldRegisterDockerContextMetaFileForReflection() {
        assertThat(RuntimeHintsPredicates.reflection().onType(DockerContextMetaFile.class))
                .accepts(hints);
    }
}
