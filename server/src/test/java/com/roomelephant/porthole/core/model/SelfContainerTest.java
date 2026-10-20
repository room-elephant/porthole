package com.roomelephant.porthole.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SelfContainerTest {
    private static final String FULL = "a".repeat(12) + "b".repeat(52);

    @Test
    void fullIdMatchesByEqualityOnRemoteSource() {
        SelfContainer self = new SelfContainer(FULL);
        assertThat(self.matches(FULL, false)).isTrue();
        assertThat(self.matches(FULL, true)).isTrue();
        assertThat(self.matches("c".repeat(64), false)).isFalse();
    }

    @Test
    void shortPrefixMatchesOnlyOnLocalSource() {
        SelfContainer self = new SelfContainer("aaaaaaaaaaaa");
        assertThat(self.matches(FULL, true)).isTrue();
        assertThat(self.matches("c".repeat(64), true)).isFalse();
    }

    @Test
    void shortPrefixIsRejectedOnRemoteSource() {
        assertThat(new SelfContainer("aaaaaaaaaaaa").matches(FULL, false)).isFalse();
    }

    @Test
    void noneMatchesNothing() {
        assertThat(SelfContainer.none().id()).isNull();
        assertThat(SelfContainer.none().matches(FULL, true)).isFalse();
        assertThat(SelfContainer.none().matches(FULL, false)).isFalse();
    }
}
