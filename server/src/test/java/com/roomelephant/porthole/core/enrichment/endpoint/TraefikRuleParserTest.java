package com.roomelephant.porthole.core.enrichment.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TraefikRuleParserTest {
    private final TraefikRuleParser parser = new TraefikRuleParser();

    @Test
    void shouldParseHostOnlyRule() {
        assertThat(parser.parse("Host(`example.com`)")).contains(new TraefikRuleParser.Route("example.com", "/"));
    }

    @Test
    void shouldParseQuotedLiteralsAndGrouping() {
        assertThat(parser.parse(" ((Host(\"EXAMPLE.COM\")) && (PathPrefix(\"/DL/\"))) "))
                .contains(new TraefikRuleParser.Route("EXAMPLE.COM", "/DL/"));
        assertThat(parser.parse("Path(`/app`) && Host(`example.com`)"))
                .contains(new TraefikRuleParser.Route("example.com", "/app"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Host(`example.com`) && Method(`POST`)",
                "Host(`a.com`) || Host(`b.com`)",
                "!Host(`example.com`)",
                "HostRegexp(`.*`)",
                "Host(`a.com`, `b.com`)",
                "Host(`${DOMAIN}`)",
                "Host(`user@example.com`)",
                "Host(`example.com/path`)",
                "Host(`example.com`",
                "Host(`example.com`))",
                "(Host(`example.com`)",
                "Host(`example.com`) &&",
                "Host(`example.com`) Path(`/app`)",
                "Host(`example.com`) && Path(`app`)",
                "Host(`example.com`) && Path(`${PATH}`)",
                "Host(`example.com`) && Path(`/app`) && PathPrefix(`/other`)",
                "Host(`a.com`) && Host(`b.com`)",
                "Path(`/app`)",
                "",
                "()",
                "Host",
                "Host(",
                "Host(example.com)",
                "Host('example.com')",
                "Host(`example.com)",
                "Host(\"example.com)",
                "Host(\"example.com\\",
                "Host(\"exa\\nmple.com\")",
                "Host(`example.com`) && Path(`/a${PATH}`)",
                "Host(`example.com`) && Path(`/a\nb`)",
                "Path(`/one`) && PathPrefix(`/two`)"
            })
    void shouldConsumeTheWholeRule(String rule) {
        assertThat(parser.parse(rule)).isEmpty();
    }

    @Test
    void shouldParseEscapedQuotesAndBackslashesInDoubleQuotedPaths() {
        assertThat(parser.parse("Host(\"example.com\") && Path(\"/a\\\\b\\\"c\")"))
                .contains(new TraefikRuleParser.Route("example.com", "/a\\b\"c"));
    }
}
