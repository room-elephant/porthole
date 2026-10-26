package com.roomelephant.porthole.core.enrichment.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class TraefikRuleParser {
    public record Route(String host, String path) {}

    private record Matcher(String name, String value) {}

    public Optional<Route> parse(String rule) {
        return new Input(rule).matchers().flatMap(this::route);
    }

    private Optional<Route> route(List<Matcher> matchers) {
        String host = null;
        String path = null;
        for (Matcher matcher : matchers) {
            if (matcher.name().equals("Host")) {
                if (host != null || !EndpointUrls.isValidHost(matcher.value())) return Optional.empty();
                host = matcher.value();
            } else {
                if (path != null
                        || !matcher.value().startsWith("/")
                        || matcher.value().contains("$" + "{")
                        || matcher.value().chars().anyMatch(Character::isISOControl)) return Optional.empty();
                path = matcher.value();
            }
        }
        if (host == null) return Optional.empty();
        return Optional.of(new Route(host, path == null ? "/" : path));
    }

    private static final class Input {
        private final String text;
        private int position;
        private int groups;
        private boolean expectTerm = true;
        private final ArrayList<Matcher> matchers = new ArrayList<>();

        private Input(String text) {
            this.text = text;
        }

        private Optional<List<Matcher>> matchers() {
            while (!end()) {
                if (expectTerm) {
                    if (!term()) return Optional.empty();
                } else if (!operator()) {
                    return Optional.empty();
                }
            }
            if (expectTerm || groups != 0) return Optional.empty();
            return Optional.of(matchers);
        }

        private boolean term() {
            if (take("(")) {
                groups++;
                return true;
            }
            Optional<Matcher> matcher = matcher();
            if (matcher.isEmpty() || matchers.size() == 2) return false;
            matchers.add(matcher.get());
            expectTerm = false;
            return true;
        }

        private boolean operator() {
            if (take(")")) {
                if (groups == 0) return false;
                groups--;
                return true;
            }
            if (take("&&")) {
                expectTerm = true;
                return true;
            }
            return false;
        }

        private void whitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
        }

        private boolean end() {
            whitespace();
            return position == text.length();
        }

        private boolean take(String token) {
            whitespace();
            if (!text.startsWith(token, position)) return false;
            position += token.length();
            return true;
        }

        private Optional<Matcher> matcher() {
            whitespace();
            int start = position;
            while (position < text.length() && Character.isLetter(text.charAt(position))) position++;
            String name = text.substring(start, position);
            if (!name.equals("Host") && !name.equals("Path") && !name.equals("PathPrefix")) return Optional.empty();
            if (!take("(")) return Optional.empty();
            Optional<String> literal = literal();
            if (literal.isEmpty() || !take(")")) return Optional.empty();
            return Optional.of(new Matcher(name, literal.get()));
        }

        private Optional<String> literal() {
            whitespace();
            if (position == text.length()) return Optional.empty();
            char quote = text.charAt(position++);
            if (quote != 96 && quote != '"') return Optional.empty();
            return quotedLiteral(quote);
        }

        private Optional<String> quotedLiteral(char quote) {
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char character = text.charAt(position++);
                if (character == quote) return Optional.of(result.toString());
                if (quote == '"' && character == '\\') {
                    if (position == text.length()) return Optional.empty();
                    character = text.charAt(position++);
                    if (character != '"' && character != '\\') return Optional.empty();
                }
                result.append(character);
            }
            return Optional.empty();
        }
    }
}
