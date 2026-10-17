package com.roomelephant.porthole.domain.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Parses complete Link values so quoted metadata cannot hide pagination. */
@NullMarked
final class RegistryLink {
    private static final String PARAMETER_ATOM = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";
    private static final Pattern PARAMETER = Pattern.compile(
            "\\s*(" + PARAMETER_ATOM + ")\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*+\"|" + PARAMETER_ATOM + ")\\s*");

    private RegistryLink() {}

    static @Nullable String next(List<String> headers) {
        String next = null;
        for (String header : headers) {
            for (String value : split(header, ',')) {
                String link = value.strip();
                int end = link.indexOf('>');
                if (!link.startsWith("<") || end < 2) throw new IllegalArgumentException("Invalid pagination link");
                if (hasNextRelation(link.substring(end + 1))) {
                    if (next != null) throw new IllegalArgumentException("Ambiguous next page");
                    next = link.substring(1, end);
                }
            }
        }
        return next;
    }

    private static boolean hasNextRelation(String parameters) {
        List<String> parts = split(parameters, ';');
        if (!parts.getFirst().isBlank()) throw new IllegalArgumentException("Invalid link suffix");
        String relation = null;
        for (String part : parts.subList(1, parts.size())) {
            var parameter = PARAMETER.matcher(part);
            if (!parameter.matches()) throw new IllegalArgumentException("Invalid link parameter");
            if (parameter.group(1).equalsIgnoreCase("rel")) {
                if (relation != null) throw new IllegalArgumentException("Duplicate link relation");
                relation = unquote(parameter.group(2));
            }
        }
        if (relation == null || relation.isBlank()) throw new IllegalArgumentException("Missing link relation");
        return Arrays.stream(relation.split("\\s+")).anyMatch("next"::equalsIgnoreCase);
    }

    private static String unquote(String value) {
        if (!value.startsWith("\"")) return value;
        var result = new StringBuilder();
        int position = 1;
        while (position < value.length() - 1) {
            char c = value.charAt(position++);
            if (c == '\\') c = value.charAt(position++);
            result.append(c);
        }
        return result.toString();
    }

    private static List<String> split(String value, char delimiter) {
        var parts = new ArrayList<String>();
        var state = new LinkState();
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            state.accept(c);
            if (c == delimiter && state.outside()) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        if (state.escaped) throw new IllegalArgumentException("Unterminated quoted escape");
        if (!state.outside()) throw new IllegalArgumentException("Unterminated link value");
        parts.add(value.substring(start));
        return parts;
    }

    private static final class LinkState {
        private boolean quoted;
        private boolean angle;
        private boolean escaped;

        private boolean outside() {
            return !quoted && !angle;
        }

        private void accept(char c) {
            if (escaped) {
                escaped = false;
                return;
            }
            if (quoted && c == '\\') {
                escaped = true;
                return;
            }
            if (c == '"') {
                quoted = !quoted;
                return;
            }
            if (!quoted) acceptTarget(c);
        }

        private void acceptTarget(char c) {
            if (c == '<') {
                if (angle) throw new IllegalArgumentException("Nested link target");
                angle = true;
            } else if (c == '>') {
                if (!angle) throw new IllegalArgumentException("Invalid link target");
                angle = false;
            }
        }
    }
}
