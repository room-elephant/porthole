package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

/** One field of a JSON Merge Patch: left out, set to null to remove it, or given a new value. */
@NullMarked
public sealed interface PatchField<T> permits PatchField.Absent, PatchField.Removed, PatchField.Set {
    @SuppressWarnings("unchecked")
    static <T> PatchField<T> absent() {
        return (PatchField<T>) Absent.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    static <T> PatchField<T> removed() {
        return (PatchField<T>) Removed.INSTANCE;
    }

    static <T> PatchField<T> set(T value) {
        return new Set<>(value);
    }

    record Absent<T>() implements PatchField<T> {
        private static final Absent<?> INSTANCE = new Absent<>();
    }

    record Removed<T>() implements PatchField<T> {
        private static final Removed<?> INSTANCE = new Removed<>();
    }

    record Set<T>(T value) implements PatchField<T> {}
}
