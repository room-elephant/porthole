package com.roomelephant.porthole.fleet.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.roomelephant.porthole.fleet.Fleet;
import com.roomelephant.porthole.fleet.Source;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SourceController.class)
class SourceControllerTest {
    private static final Instant T = Instant.parse("2026-11-08T10:00:00Z");
    private static final Source WALKER = new Source("walker", "Walker", null, true, Duration.ofSeconds(1));
    private static final Source MIKE = new Source("mike", "Mike", null, false, Duration.ofSeconds(1));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Fleet fleet;

    @Test
    void listsSourcesInCatalogOrder() throws Exception {
        when(fleet.snapshots())
                .thenReturn(
                        List.of(SourceSnapshot.initial(WALKER).succeeded(List.of(), T), SourceSnapshot.initial(MIKE)));
        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("walker"))
                .andExpect(jsonPath("$[0].name").value("Walker"))
                .andExpect(jsonPath("$[0].status").value("UP"))
                .andExpect(jsonPath("$[0].error").doesNotExist())
                .andExpect(jsonPath("$[0].lastSuccessAt").value("2026-11-08T10:00:00Z"))
                .andExpect(jsonPath("$[1].id").value("mike"))
                .andExpect(jsonPath("$[1].error").value("Waiting for first check"))
                .andExpect(jsonPath("$[1].lastSuccessAt").doesNotExist());
    }

    @Test
    void returns200WhenEverySourceIsDown() throws Exception {
        var failed = SourceSnapshot.initial(WALKER)
                .failed(SourceSnapshot.Status.DOWN, "Docker is not reachable", T, Duration.ofHours(24));
        when(fleet.snapshots()).thenReturn(List.of(failed, SourceSnapshot.initial(MIKE)));
        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DOWN"))
                .andExpect(jsonPath("$[0].error").value("Docker is not reachable"))
                .andExpect(jsonPath("$[1].status").value("DOWN"));
    }
}
