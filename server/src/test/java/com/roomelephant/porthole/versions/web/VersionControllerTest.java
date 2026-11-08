package com.roomelephant.porthole.versions.web;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roomelephant.porthole.core.enrichment.version.refresh.VersionRefreshService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VersionController.class)
class VersionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VersionRefreshService versions;

    @Test
    void acceptsManualVersionRefresh() throws Exception {
        mockMvc.perform(post("/api/versions/refresh"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        verify(versions).startCheck();
    }

    @Test
    void doesNotAllowCrossOriginRefreshPreflight() throws Exception {
        mockMvc.perform(options("/api/versions/refresh")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verify(versions, never()).startCheck();
    }
}
