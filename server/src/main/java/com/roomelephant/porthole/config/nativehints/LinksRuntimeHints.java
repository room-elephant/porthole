package com.roomelephant.porthole.config.nativehints;

import com.roomelephant.porthole.core.model.ExternalLink;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/** The link store reads an array, which a native image can only create when it's registered. */
@NullMarked
public class LinksRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.reflection().registerType(ExternalLink[].class);
    }
}
