package com.roomelephant.porthole.entries;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/** Durable storage for uploaded icon files, named by their content-addressed ID. */
@NullMarked
public interface IconFileStore {
    /** Writes the file unless one with this ID exists. @return whether a new file was written */
    boolean store(String id, byte[] bytes);

    /** Marks the file as just used, so a sweep keeps it for a while. @return false when there is no such file */
    boolean touch(String id);

    Optional<byte[]> read(String id);

    int count();

    /** Deletes upload files and leftover temporary files not in {@code keep} and last modified before the cutoff. */
    int deleteUnreferenced(Set<String> keep, Instant modifiedBefore);
}
