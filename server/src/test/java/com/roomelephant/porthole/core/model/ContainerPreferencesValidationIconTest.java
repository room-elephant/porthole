package com.roomelephant.porthole.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContainerPreferencesValidationIconTest {
    private final ContainerPreferencesKey key = new ContainerPreferencesKey("local", "grafana");

    @ParameterizedTest
    @ValueSource(strings = {"coredns", "7-days-to-die", "a.b_c", "1"})
    void acceptsLibraryNames(String name) {
        assertThatCode(() -> ContainerPreferencesValidation.validateIcon(key, SelectedIcon.library(name)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "../x", "a/b", "Upper", "-leading", ".hidden", "with space"})
    void rejectsLibraryNames(String name) {
        assertThatThrownBy(() -> ContainerPreferencesValidation.validateIcon(key, SelectedIcon.library(name)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLibraryNameLongerThan255Characters() {
        SelectedIcon longest = SelectedIcon.library("a".repeat(255));
        assertThatCode(() -> ContainerPreferencesValidation.validateIcon(key, longest))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () -> ContainerPreferencesValidation.validateIcon(key, SelectedIcon.library("a".repeat(256))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0123456789abcdef0123456789abcdef.png",
                "0123456789abcdef0123456789abcdef.webp",
                "0123456789abcdef0123456789abcdef.jpg"
            })
    void acceptsUploadNames(String name) {
        SelectedIcon icon = SelectedIcon.upload(name);
        assertThat(icon.selectsUpload()).isTrue();
        assertThatCode(() -> ContainerPreferencesValidation.validateIcon(key, icon))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "abc.svg",
                "coredns",
                "0123456789abcdef0123456789abcdef.svg",
                "0123456789ABCDEF0123456789ABCDEF.png",
                "../0123456789abcdef0123456789abcdef.png"
            })
    void rejectsUploadNames(String name) {
        assertThatThrownBy(() -> ContainerPreferencesValidation.validateIcon(key, SelectedIcon.upload(name)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownTypeAndMissingValues() {
        assertThatThrownBy(() -> ContainerPreferencesValidation.validateIcon(key, new SelectedIcon("other", "coredns")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContainerPreferencesValidation.validateIcon(key, new SelectedIcon(null, "coredns")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContainerPreferencesValidation.validateIcon(key, new SelectedIcon("library", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidKey() {
        ContainerPreferencesKey blank = new ContainerPreferencesKey(" ", "grafana");
        assertThatThrownBy(() -> ContainerPreferencesValidation.validateIcon(blank, SelectedIcon.library("coredns")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
