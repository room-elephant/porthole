package com.roomelephant.porthole.inbound.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roomelephant.porthole.core.model.PresentedLink;
import com.roomelephant.porthole.core.port.inbound.ListLinks;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LinkController.class)
class LinkControllerTest {
    private static final String ID = "link:3f2a9c1e5b7d4e8a9c217d4e5f6a8b90";
    private static final String ICON = "/api/icons/" + "a".repeat(64) + ".png";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListLinks links;

    @Test
    void listsLinkEntriesWithResolvedIcon() throws Exception {
        org.mockito.Mockito.when(links.list())
                .thenReturn(List.of(new PresentedLink(ID, "Router", "http://r/", "base", ICON)));

        mockMvc.perform(get("/api/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID))
                .andExpect(jsonPath("$[0].name").value("Router"))
                .andExpect(jsonPath("$[0].url").value("http://r/"))
                .andExpect(jsonPath("$[0].stack").value("base"))
                .andExpect(jsonPath("$[0].iconUrl").value(ICON));
    }

    @Test
    void writesMovedToEntries() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Router\",\"url\":\"http://r/\"}"))
                .andExpect(status().is4xxClientError());
    }
}
