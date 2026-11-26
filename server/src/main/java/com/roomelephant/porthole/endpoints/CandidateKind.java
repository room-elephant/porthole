package com.roomelephant.porthole.endpoints;

import org.jspecify.annotations.NullMarked;

/** How a candidate reaches the service: through a proxy route, or directly on a published port. */
@NullMarked
public enum CandidateKind {
    ROUTED,
    DIRECT
}
