package com.roomelephant.porthole.endpoints;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class PublishedHostResolver {
    public String resolve(@Nullable String publishedHost) {
        return publishedHost == null ? "127.0.0.1" : publishedHost;
    }
}
