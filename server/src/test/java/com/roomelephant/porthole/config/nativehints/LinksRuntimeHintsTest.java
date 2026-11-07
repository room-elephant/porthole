package com.roomelephant.porthole.config.nativehints;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.core.model.ExternalLink;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class LinksRuntimeHintsTest {
    @Test
    void registersTheArrayTheLinkStoreReads() {
        RuntimeHints hints = new RuntimeHints();

        new LinksRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(ExternalLink[].class))
                .accepts(hints);
    }
}
