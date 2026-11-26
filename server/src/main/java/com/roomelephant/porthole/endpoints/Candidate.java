package com.roomelephant.porthole.endpoints;

import org.jspecify.annotations.NullMarked;

/** An address discovered for a container. The ID names its provider, so it stays stable across hosts. */
@NullMarked
public record Candidate(String id, String source, String url, CandidateKind kind) {}
