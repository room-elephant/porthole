package com.roomelephant.porthole.entries;

import com.roomelephant.porthole.shared.HttpUrls;
import org.jspecify.annotations.NullMarked;

/** Which address an entry opens: a discovered candidate by ID, a URL the user typed, or none. */
@NullMarked
public sealed interface EndpointChoice permits EndpointChoice.Discovered, EndpointChoice.Custom, EndpointChoice.None {
    int MAX_ID_LENGTH = 255;

    EndpointChoice NONE = new None();

    record Discovered(String id) implements EndpointChoice {
        public Discovered {
            if (id.isBlank()) throw new IllegalArgumentException("endpoint.id must not be blank");
            if (id.length() > MAX_ID_LENGTH)
                throw new IllegalArgumentException("endpoint.id must be at most " + MAX_ID_LENGTH + " characters");
        }
    }

    record Custom(String url) implements EndpointChoice {
        public Custom {
            if (!HttpUrls.isValid(url))
                throw new IllegalArgumentException("endpoint.url must be an absolute HTTP(S) URL without credentials,"
                        + " at most " + HttpUrls.MAX_LENGTH + " characters");
        }
    }

    record None() implements EndpointChoice {}
}
