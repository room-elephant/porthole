package com.roomelephant.porthole.core.model;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A page the user added to the dashboard. The ID is generated because a link can be renamed. */
@NullMarked
public record ExternalLink(
        String id,
        String name,
        String url,
        @Nullable String stack,
        @Nullable SelectedIcon icon) {
    public static final Pattern ID = Pattern.compile("^[0-9a-f]{32}$");

    /** @param details values already passed through {@link ExternalLinkValidation#normalize} */
    public ExternalLink withDetails(ExternalLinkDetails details) {
        return new ExternalLink(
                id,
                Objects.requireNonNull(details.name()),
                Objects.requireNonNull(details.url()),
                details.stack(),
                icon);
    }

    public ExternalLink withIcon(SelectedIcon selected) {
        return new ExternalLink(id, name, url, stack, selected);
    }
}
