package com.roomelephant.porthole.outbound.registry.distribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Parses complete Link values so quoted metadata cannot hide pagination. */
@NullMarked
final class LinkHeaderParser {
    private static final String PARAMETER_ATOM = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";
    private static final Pattern PARAMETER = Pattern.compile(
            "\\s*(" + PARAMETER_ATOM + ")\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*+\"|" + PARAMETER_ATOM + ")\\s*");

    private LinkHeaderParser() {}

    static @Nullable String next(List<String> headers) {
        String next = null;
        for (String header : headers) {
            for (String value : split(header, ',')) {
                String link = value.strip();
                int end = link.indexOf('>');
                if (!link.startsWith("<") || end < 2) throw new RegistryProtocolException("Invalid pagination link");
                if (hasNextRelation(link.substring(end + 1))) {
                    if (next != null) throw new RegistryProtocolException("Ambiguous next page");
                    next = link.substring(1, end);
                }
            }
        }
        return next;
    }

    private static boolean hasNextRelation(String parameters) {
        List<String> parts = split(parameters, ';');
        if (!parts.getFirst().isBlank()) throw new RegistryProtocolException("Invalid link suffix");
        String relation = null;
        for (String part : parts.subList(1, parts.size())) {
            Matcher parameter = PARAMETER.matcher(part);
            if (!parameter.matches()) throw new RegistryProtocolException("Invalid link parameter");
            if (parameter.group(1).equalsIgnoreCase("rel")) {
                if (relation != null) throw new RegistryProtocolException("Duplicate link relation");
                relation = unquote(parameter.group(2));
            }
        }
        if (relation == null || relation.isBlank()) throw new RegistryProtocolException("Missing link relation");
        return Arrays.stream(relation.split("\\s+")).anyMatch("next"::equalsIgnoreCase);
    }

    private static String unquote(String value) {
        if (!value.startsWith("\"")) return value;
        StringBuilder result = new StringBuilder();
        int position = 1;
        while (position < value.length() - 1) {
            char c = value.charAt(position++);
            if (c == '\\') c = value.charAt(position++);
            result.append(c);
        }
        return result.toString();
    }

    private static List<String> split(String value, char delimiter) {
        List<String> parts = new ArrayList<>();
        LinkState state = new LinkState();
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            state.accept(c);
            if (c == delimiter && state.outside()) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        if (state.escaped) throw new RegistryProtocolException("Unterminated quoted escape");
        if (!state.outside()) throw new RegistryProtocolException("Unterminated link value");
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
                if (angle) throw new RegistryProtocolException("Nested link target");
                angle = true;
            } else if (c == '>') {
                if (!angle) throw new RegistryProtocolException("Invalid link target");
                angle = false;
            }
        }
    }
}
