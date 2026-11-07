package com.roomelephant.porthole.core.port.outbound;

import com.roomelephant.porthole.core.model.IconImageType;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/** Durable storage for uploaded icon files. Names are generated here and never come from callers' input. */
@NullMarked
public interface IconFileStore {
    String store(byte[] bytes, IconImageType type);

    Optional<byte[]> read(String name);

    void delete(String name);

    int count();

    void retainOnly(Set<String> names);
}
