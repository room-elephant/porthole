package com.roomelephant.porthole.core.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Outcome of a remote metadata lookup, independent of transport. */
@NullMarked
@SuppressWarnings("java:S2326") // T keeps each outcome tied to the requested metadata type.
public sealed interface RegistryLookup<T> {
    record Available<T>(T value) implements RegistryLookup<T> {
        public Available {
            Objects.requireNonNull(value);
        }
    }

    record Missing<T>() implements RegistryLookup<T> {}

    record Unsupported<T>() implements RegistryLookup<T> {}

    record Unavailable<T>(String reason) implements RegistryLookup<T> {
        public Unavailable {
            Objects.requireNonNull(reason);
        }
    }
}
