package com.roomelephant.porthole.entries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.entries.EndpointChoice.Custom;
import com.roomelephant.porthole.entries.EndpointChoice.Discovered;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.Icon.Library;
import com.roomelephant.porthole.entries.Icon.Upload;
import org.junit.jupiter.api.Test;

class EntryRecordTest {
    private static final LinkRef LINK = new LinkRef("3f2a9c1e5b7d4e8a9c217d4e5f6a8b90");
    private static final ContainerRef CONTAINER = new ContainerRef("walker", "app");
    private static final Custom URL = new Custom("http://192.168.50.1/");
    private static final String HASH = "a".repeat(64);

    @Test
    void linkRequiresNameAndCustomEndpoint() {
        assertThatThrownBy(() -> new EntryRecord(LINK, null, null, URL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link needs a name");
        assertThatThrownBy(() -> new EntryRecord(LINK, "Router", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link needs a URL");
        assertThatThrownBy(() -> new EntryRecord(LINK, "Router", null, new Discovered("traefik:x"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link's endpoint must be a URL");
        assertThatThrownBy(() -> new EntryRecord(LINK, "Router", null, EndpointChoice.NONE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A link's endpoint must be a URL");
    }

    @Test
    void containerRejectsStack() {
        assertThatThrownBy(() -> new EntryRecord(CONTAINER, null, null, null, "base"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Containers take their stack from Docker Compose");
    }

    @Test
    void trimsNameAndStackAndBlankStackIsNull() {
        EntryRecord record = new EntryRecord(LINK, "  Router ", null, URL, " base ");

        assertThat(record.name()).isEqualTo("Router");
        assertThat(record.stack()).isEqualTo("base");
        assertThat(new EntryRecord(LINK, "Router", null, URL, "   ").stack()).isNull();
    }

    @Test
    void rejectsNameOver255AndBlankName() {
        assertThatCode(() -> new EntryRecord(LINK, "a".repeat(255), null, URL, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new EntryRecord(LINK, "a".repeat(256), null, URL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must be at most 255 characters");
        assertThatThrownBy(() -> new EntryRecord(CONTAINER, "  ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
        assertThatThrownBy(() -> new EntryRecord(LINK, "Router", null, URL, "s".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stack must be at most 255 characters");
    }

    @Test
    void emptyContainerRecordIsEmpty() {
        assertThat(EntryRecord.container(CONTAINER).isEmpty()).isTrue();
        assertThat(new EntryRecord(CONTAINER, "x", null, null, null).isEmpty()).isFalse();
        assertThat(new EntryRecord(CONTAINER, null, new Library("coredns"), null, null).isEmpty())
                .isFalse();
        assertThat(new EntryRecord(CONTAINER, null, null, EndpointChoice.NONE, null).isEmpty())
                .isFalse();
        assertThat(new EntryRecord(LINK, "Router", null, URL, null).isEmpty()).isFalse();
    }

    @Test
    void iconPatterns() {
        assertThatCode(() -> new Library("coredns")).doesNotThrowAnyException();
        assertThatThrownBy(() -> new Library("Bad Name")).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new Upload(HASH + ".png")).doesNotThrowAnyException();
        assertThatCode(() -> new Upload(HASH + ".svg")).doesNotThrowAnyException();
        assertThatThrownBy(() -> new Upload("a".repeat(32) + ".png")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Upload(HASH + ".gif")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customEndpointRequiresHttpUrl() {
        assertThatThrownBy(() -> new Custom("ftp://x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Custom("http://user:pw@x/")).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new Custom("https://x.example/")).doesNotThrowAnyException();
    }

    @Test
    void discoveredEndpointRequiresIdUpTo255() {
        assertThatThrownBy(() -> new Discovered(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Discovered("a".repeat(256))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new Discovered("traefik:https://app/")).doesNotThrowAnyException();
    }
}
