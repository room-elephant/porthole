package com.roomelephant.porthole.entries.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.entries.Icon;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Keeps the sample data that `make lab` runs with loadable, with every uploaded icon it references present. */
class LabDataTest {
    private static final Path LAB_DATA = Path.of("..", "lab", "data");

    @Test
    void labStateLoadsAndItsUploadsExist() {
        EntryState state = new JsonStateStore(
                        LAB_DATA.resolve("state.json"), JsonMapper.builder().build())
                .load();

        assertThat(state.entries()).isNotEmpty();
        assertThat(state.linkCount()).isPositive();
        for (String upload : state.uploadIds()) {
            assertThat(upload).matches(Icon.UPLOAD_ID);
            assertThat(LAB_DATA.resolve("icons").resolve(upload)).exists();
        }
        assertThat(state.entries()).anySatisfy(entry -> assertThat(entry.icon()).isInstanceOf(Icon.Upload.class));
        assertThat(state.entries()).as("a renamed container").anySatisfy(entry -> {
            assertThat(entry.ref()).isInstanceOf(com.roomelephant.porthole.entries.EntryRef.ContainerRef.class);
            assertThat(entry.name()).isNotNull();
        });
        assertThat(Files.exists(LAB_DATA.resolve("state.json"))).isTrue();
    }
}
