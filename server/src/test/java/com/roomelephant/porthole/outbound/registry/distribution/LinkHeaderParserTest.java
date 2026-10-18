package com.roomelephant.porthole.outbound.registry.distribution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkHeaderParserTest {
    @Test
    void longQuotedMetadataDoesNotOverflowTheStack() {
        String header = "<?page=2>; title=\"" + "x".repeat(20000) + "\"; rel=next";
        assertEquals("?page=2", LinkHeaderParser.next(List.of(header)));
    }

    @Test
    void escapedQuotesAndDelimitersRemainInsideMetadata() {
        assertEquals(
                "?page=2",
                LinkHeaderParser.next(
                        List.of("<?page=1>; rel=prev, <?page=2>; title=\"a\\\"b; c,d\"; rel=\"prev next\"")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<?p=2>; title=\"unterminated",
                "<?p=2>; title=\"escape\\",
                "<<target>>; rel=next",
                "target>; rel=next"
            })
    void rejectsMalformedLinkStructure(String header) {
        var headers = List.of(header);
        assertThrows(RegistryProtocolException.class, () -> LinkHeaderParser.next(headers));
    }
}
