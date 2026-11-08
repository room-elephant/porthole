package com.roomelephant.porthole.versions;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record TagCatalogue(List<String> tags) {
    public TagCatalogue {
        tags = List.copyOf(tags);
    }
}
