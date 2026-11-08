package com.roomelephant.porthole.dashboard.web;

import static com.roomelephant.porthole.entries.PatchField.absent;
import static com.roomelephant.porthole.entries.PatchField.removed;
import static com.roomelephant.porthole.entries.PatchField.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EntryPatch;
import com.roomelephant.porthole.entries.Icon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EntryPatchReaderTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String UPLOAD = "a".repeat(64) + ".png";

    private static EntryPatch read(String json) {
        return EntryPatchReader.read(MAPPER.readTree(json));
    }

    @Test
    void absentMeansAbsent() {
        assertThat(read("{}")).isEqualTo(new EntryPatch(absent(), absent(), absent(), absent()));
    }

    @Test
    void nullMeansRemoved() {
        assertThat(read("{\"name\":null,\"icon\":null,\"endpoint\":null,\"stack\":null}"))
                .isEqualTo(new EntryPatch(removed(), removed(), removed(), removed()));
    }

    @Test
    void readsNameAndStack() {
        assertThat(read("{\"name\":\"App\",\"stack\":\"base\"}"))
                .isEqualTo(new EntryPatch(set("App"), absent(), absent(), set("base")));
    }

    @Test
    void readsEveryEndpointShape() {
        assertThat(read("{\"endpoint\":{\"id\":\"traefik:https://a/\"}}").endpoint())
                .isEqualTo(set(new EndpointChoice.Discovered("traefik:https://a/")));
        assertThat(read("{\"endpoint\":{\"url\":\"https://a.example/\"}}").endpoint())
                .isEqualTo(set(new EndpointChoice.Custom("https://a.example/")));
        assertThat(read("{\"endpoint\":\"none\"}").endpoint()).isEqualTo(set(EndpointChoice.NONE));
    }

    @Test
    void readsEveryIconShape() {
        assertThat(read("{\"icon\":{\"library\":\"coredns\"}}").icon()).isEqualTo(set(new Icon.Library("coredns")));
        assertThat(read("{\"icon\":{\"upload\":\"" + UPLOAD + "\"}}").icon()).isEqualTo(set(new Icon.Upload(UPLOAD)));
    }

    @Test
    void rejectsUnknownField() {
        assertThatThrownBy(() -> read("{\"colour\":\"red\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown field: colour");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "\"x\"",
                "{\"icon\":{\"library\":\"a\",\"upload\":\"b\"}}",
                "{\"icon\":{\"emoji\":\"x\"}}",
                "{\"icon\":\"coredns\"}",
                "{\"icon\":{\"library\":3}}",
                "{\"endpoint\":\"auto\"}",
                "{\"endpoint\":{\"id\":\"a\",\"url\":\"b\"}}",
                "{\"endpoint\":{\"source\":\"traefik\"}}",
                "{\"endpoint\":3}",
                "{\"name\":3}",
                "{\"stack\":true}"
            })
    void rejectsMalformedValues(String json) {
        assertThatThrownBy(() -> read(json)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIconWithTwoKeys() {
        assertThatThrownBy(() -> read("{\"icon\":{\"library\":\"a\",\"upload\":\"b\"}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("icon must be {\"library\": name} or {\"upload\": id}");
    }

    @Test
    void rejectsNumericName() {
        assertThatThrownBy(() -> read("{\"name\":3}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must be a string or null");
    }

    @Test
    void readsIconForLinkCreation() {
        JsonNode icon = MAPPER.readTree("{\"library\":\"router\"}");

        assertThat(EntryPatchReader.icon(icon)).isEqualTo(new Icon.Library("router"));
    }
}
