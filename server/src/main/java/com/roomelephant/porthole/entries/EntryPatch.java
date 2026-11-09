package com.roomelephant.porthole.entries;

import org.jspecify.annotations.NullMarked;

/** A JSON Merge Patch of an entry's editable fields. */
@NullMarked
public record EntryPatch(
        PatchField<String> name,
        PatchField<Icon> icon,
        PatchField<EndpointChoice> endpoint,
        PatchField<String> stack) {}
