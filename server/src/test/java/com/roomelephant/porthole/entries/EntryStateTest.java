package com.roomelephant.porthole.entries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.entries.EndpointChoice.Custom;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.Icon.Library;
import com.roomelephant.porthole.entries.Icon.Upload;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntryStateTest {
    private static final ContainerRef APP = new ContainerRef("walker", "app");
    private static final ContainerRef DB = new ContainerRef("walker", "db");
    private static final LinkRef ROUTER = new LinkRef("3f2a9c1e5b7d4e8a9c217d4e5f6a8b90");
    private static final String UPLOAD = "b".repeat(64) + ".png";

    private static EntryRecord link(String name) {
        return new EntryRecord(ROUTER, name, null, new Custom("http://192.168.50.1/"), null);
    }

    @Test
    void withReplacesInPlaceAndAppendsNew() {
        EntryState state = EntryState.EMPTY
                .with(new EntryRecord(APP, "App", null, null, null))
                .with(link("Router"))
                .with(new EntryRecord(DB, "Db", null, null, null));

        EntryState replaced = state.with(new EntryRecord(APP, "Renamed", null, null, null));

        assertThat(replaced.entries()).extracting(EntryRecord::ref).containsExactly(APP, ROUTER, DB);
        assertThat(replaced.find(APP)).map(EntryRecord::name).contains("Renamed");
        assertThat(state.find(APP)).map(EntryRecord::name).contains("App");
    }

    @Test
    void withDropsEmptyContainerRecord() {
        EntryState state = EntryState.EMPTY.with(new EntryRecord(APP, "App", null, null, null));

        assertThat(state.with(EntryRecord.container(APP)).entries()).isEmpty();
        assertThat(EntryState.EMPTY.with(EntryRecord.container(APP)).entries()).isEmpty();
    }

    @Test
    void withoutRemoves() {
        EntryState state = EntryState.EMPTY.with(link("Router"));

        assertThat(state.without(ROUTER).find(ROUTER)).isEmpty();
        assertThat(state.without(APP)).isEqualTo(state);
    }

    @Test
    void rejectsDuplicateRefs() {
        assertThatThrownBy(() -> new EntryState(List.of(link("a"), link("b"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate entry id: " + ROUTER.id());
    }

    @Test
    void uploadIdsCollectsOnlyUploads() {
        EntryState state = EntryState.EMPTY
                .with(new EntryRecord(APP, null, new Upload(UPLOAD), null, null))
                .with(new EntryRecord(DB, null, new Library("postgres"), null, null));

        assertThat(state.uploadIds()).containsExactly(UPLOAD);
    }

    @Test
    void linkCountCountsLinks() {
        EntryState state = EntryState.EMPTY
                .with(new EntryRecord(APP, "App", null, null, null))
                .with(link("Router"));

        assertThat(state.linkCount()).isEqualTo(1);
    }

    @Test
    void entriesAreImmutable() {
        EntryState state = EntryState.EMPTY.with(link("Router"));

        assertThatThrownBy(() -> state.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
