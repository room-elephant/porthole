package com.roomelephant.porthole.entries.web;

import static com.roomelephant.porthole.entries.PatchField.absent;
import static com.roomelephant.porthole.entries.PatchField.removed;
import static com.roomelephant.porthole.entries.PatchField.set;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roomelephant.porthole.entries.EntryNotDeletableException;
import com.roomelephant.porthole.entries.EntryNotFoundException;
import com.roomelephant.porthole.entries.EntryPatch;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.EntryService;
import com.roomelephant.porthole.entries.Icon;
import com.roomelephant.porthole.entries.LinkDraft;
import com.roomelephant.porthole.entries.LinkLimitReachedException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EntryController.class)
class EntryControllerTest {
    private static final LinkRef ROUTER = new LinkRef("3f2a9c1e5b7d4e8a9c217d4e5f6a8b90");
    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EntryService entries;

    @Test
    void createReturns201WithId() throws Exception {
        when(entries.createLink(any())).thenReturn(ROUTER);

        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Router\",\"url\":\"http://192.168.50.1/\",\"stack\":\"base\","
                                + "\"icon\":{\"library\":\"router\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ROUTER.id()));

        verify(entries).createLink(new LinkDraft("Router", "http://192.168.50.1/", "base", new Icon.Library("router")));
    }

    @Test
    void createWithoutIconPassesNull() throws Exception {
        when(entries.createLink(any())).thenReturn(ROUTER);

        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Router\",\"url\":\"http://192.168.50.1/\"}"))
                .andExpect(status().isCreated());

        verify(entries).createLink(new LinkDraft("Router", "http://192.168.50.1/", null, null));
    }

    @Test
    void createInvalidIs400ProblemJson() throws Exception {
        when(entries.createLink(any())).thenThrow(new IllegalArgumentException("A link needs a name"));

        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://192.168.50.1/\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("A link needs a name"));
    }

    @Test
    void createWithMalformedIconIs400() throws Exception {
        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Router\",\"url\":\"http://x/\",\"icon\":\"router\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAtLimitIs507() throws Exception {
        when(entries.createLink(any())).thenThrow(new LinkLimitReachedException(500));

        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Router\",\"url\":\"http://192.168.50.1/\"}"))
                .andExpect(status().isInsufficientStorage())
                .andExpect(jsonPath("$.detail").value("Porthole has reached its limit of 500 links"));
    }

    @Test
    void patchPassesParsedPatch() throws Exception {
        mockMvc.perform(patch("/api/entries/{id}", ROUTER.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"icon\":null}"))
                .andExpect(status().isNoContent());

        verify(entries).update(ROUTER.id(), new EntryPatch(set("x"), removed(), absent(), absent()));
    }

    @Test
    void patchAcceptsMergePatchContentType() throws Exception {
        mockMvc.perform(patch("/api/entries/{id}", ROUTER.id())
                        .contentType(MERGE_PATCH)
                        .content("{\"stack\":\"base\"}"))
                .andExpect(status().isNoContent());

        verify(entries).update(ROUTER.id(), new EntryPatch(absent(), absent(), absent(), set("base")));
    }

    @Test
    void patchAcceptsDottedNamesAndEncodedColons() throws Exception {
        EntryPatch rename = new EntryPatch(set("App"), absent(), absent(), absent());

        mockMvc.perform(patch(URI.create("/api/entries/container:local:My.App-1"))
                        .contentType(MERGE_PATCH)
                        .content("{\"name\":\"App\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch(URI.create("/api/entries/container%3Alocal%3AMy.App-1"))
                        .contentType(MERGE_PATCH)
                        .content("{\"name\":\"App\"}"))
                .andExpect(status().isNoContent());

        verify(entries, org.mockito.Mockito.times(2)).update("container:local:My.App-1", rename);
    }

    @Test
    void patchWithUnknownFieldIs400() throws Exception {
        mockMvc.perform(patch("/api/entries/{id}", ROUTER.id())
                        .contentType(MERGE_PATCH)
                        .content("{\"colour\":\"red\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown field: colour"));
    }

    @Test
    void patchUnknownIs404() throws Exception {
        doThrow(new EntryNotFoundException("link:" + "0".repeat(32)))
                .when(entries)
                .update(anyString(), any());

        mockMvc.perform(patch("/api/entries/{id}", "link:" + "0".repeat(32))
                        .contentType(MERGE_PATCH)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No entry link:" + "0".repeat(32)));
    }

    @Test
    void deleteContainerIs405() throws Exception {
        doThrow(new EntryNotDeletableException()).when(entries).delete("container:walker:app");

        mockMvc.perform(delete("/api/entries/{id}", "container:walker:app"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "PATCH"))
                .andExpect(jsonPath("$.detail").value("Container entries can't be deleted"));
    }

    @Test
    void deleteLinkIs204() throws Exception {
        mockMvc.perform(delete("/api/entries/{id}", ROUTER.id())).andExpect(status().isNoContent());

        verify(entries).delete(eq(ROUTER.id()));
    }
}
