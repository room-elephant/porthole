package com.roomelephant.porthole.entries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EntryRefTest {

    @Test
    void containerIdRoundTrips() {
        EntryRef ref = EntryRef.parse("container:walker:smh-sonarr-1");

        assertThat(ref).isEqualTo(new ContainerRef("walker", "smh-sonarr-1"));
        assertThat(ref.id()).isEqualTo("container:walker:smh-sonarr-1");
    }

    @Test
    void linkIdRoundTrips() {
        EntryRef ref = EntryRef.parse("link:3f2a9c1e5b7d4e8a9c217d4e5f6a8b90");

        assertThat(ref).isEqualTo(new LinkRef("3f2a9c1e5b7d4e8a9c217d4e5f6a8b90"));
        assertThat(ref.id()).isEqualTo("link:3f2a9c1e5b7d4e8a9c217d4e5f6a8b90");
    }

    @Test
    void acceptsDottedAndCapitalisedContainerNames() {
        assertThat(EntryRef.parse("container:local:My.App-1")).isEqualTo(new ContainerRef("local", "My.App-1"));
    }

    static java.util.stream.Stream<String> malformed() {
        return java.util.stream.Stream.of(
                "",
                "container:walker",
                "container:Walker:app",
                "container:walker:-app",
                "container:walker:a:b",
                "container::app",
                "link:ABC",
                "link:3f2a",
                "link:3F2A9C1E5B7D4E8A9C217D4E5F6A8B90",
                "link:3f2a9c1e5b7d4e8a9c217d4e5f6a8b90:x",
                "thing:x",
                "container:walker:" + "a".repeat(256));
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void rejectsMalformedIds(String id) {
        assertThatThrownBy(() -> EntryRef.parse(id))
                .isInstanceOf(EntryNotFoundException.class)
                .hasMessage("No entry " + id);
    }

    @Test
    void randomLinkRefIs32Hex() {
        LinkRef ref = LinkRef.random();

        assertThat(ref.hex()).matches("[0-9a-f]{32}");
        assertThat(LinkRef.random()).isNotEqualTo(ref);
    }
}
