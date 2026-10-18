package com.roomelephant.porthole.outbound.registry;

import java.net.URI;
import java.util.List;
import org.jspecify.annotations.*;

@NullMarked
public record TagPage(List<String> tags, @Nullable URI next) {
    public TagPage {
        tags = List.copyOf(tags);
    }
}
