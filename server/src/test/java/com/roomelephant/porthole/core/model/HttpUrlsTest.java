package com.roomelephant.porthole.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class HttpUrlsTest {
    @ParameterizedTest
    @ValueSource(strings = {"http://192.168.50.1/", "HTTPS://nas.lan:5001/", "http://[::1]:8080/", "http://router"})
    void acceptsAbsoluteHttpUrls(String url) {
        assertThat(HttpUrls.isValid(url)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "192.168.50.1",
                "javascript:alert(1)",
                "ftp://x/",
                "http://user:pw@x/",
                "http://",
                "http://bad host/"
            })
    void rejectsAnythingElse(String url) {
        assertThat(HttpUrls.isValid(url)).isFalse();
    }

    @Test
    void enforcesMaximumLength() {
        String prefix = "http://x/";
        assertThat(HttpUrls.isValid(prefix + "a".repeat(HttpUrls.MAX_LENGTH - prefix.length())))
                .isTrue();
        assertThat(HttpUrls.isValid(prefix + "a".repeat(HttpUrls.MAX_LENGTH - prefix.length() + 1)))
                .isFalse();
    }
}
