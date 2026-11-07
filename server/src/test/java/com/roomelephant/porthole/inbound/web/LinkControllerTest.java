package com.roomelephant.porthole.inbound.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roomelephant.porthole.core.model.ExternalLinkDetails;
import com.roomelephant.porthole.core.model.PresentedLink;
import com.roomelephant.porthole.core.model.exception.IconLimitReachedException;
import com.roomelephant.porthole.core.model.exception.LinkLimitReachedException;
import com.roomelephant.porthole.core.model.exception.LinkNotFoundException;
import com.roomelephant.porthole.core.port.inbound.ManageLinks;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@WebMvcTest(LinkController.class)
class LinkControllerTest {
    private static final String ID = "3f2a9c1e5b7d4e8a9c217d4e5f6a8b90";
    private static final String BODY = "{\"name\":\"Router\",\"url\":\"http://r/\",\"stack\":\"base\"}";
    private static final ExternalLinkDetails DETAILS = new ExternalLinkDetails("Router", "http://r/", "base");
    private static final PresentedLink LINK = new PresentedLink(ID, "Router", "http://r/", "base", null);
    private static final byte[] PNG = {1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageLinks links;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestBuilder upload() {
        return multipart("/api/links/" + ID + "/icon/upload")
                .file(new MockMultipartFile("file", "logo.png", "image/png", PNG));
    }

    @Test
    void listReturnsLinks() throws Exception {
        when(links.list()).thenReturn(List.of(LINK));

        mockMvc.perform(get("/api/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID))
                .andExpect(jsonPath("$[0].name").value("Router"))
                .andExpect(jsonPath("$[0].url").value("http://r/"))
                .andExpect(jsonPath("$[0].stack").value("base"))
                .andExpect(jsonPath("$[0].iconUrl").isEmpty());
    }

    @Test
    void createReturns201WithTheLink() throws Exception {
        when(links.create(DETAILS)).thenReturn(LINK);

        mockMvc.perform(json(post("/api/links"), BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID));
    }

    @Test
    void createInvalidReturns400() throws Exception {
        when(links.create(any())).thenThrow(new IllegalArgumentException("bad"));

        mockMvc.perform(json(post("/api/links"), BODY)).andExpect(status().isBadRequest());
    }

    @Test
    void createMalformedBodyReturns400() throws Exception {
        mockMvc.perform(json(post("/api/links"), "not json")).andExpect(status().isBadRequest());

        verify(links, never()).create(any());
    }

    @Test
    void createAtLimitReturns507() throws Exception {
        when(links.create(any())).thenThrow(new LinkLimitReachedException(500));

        mockMvc.perform(json(post("/api/links"), BODY)).andExpect(status().isInsufficientStorage());
    }

    @Test
    void createPersistFailureReturns500() throws Exception {
        when(links.create(any())).thenThrow(new IllegalStateException("disk full"));

        mockMvc.perform(json(post("/api/links"), BODY)).andExpect(status().isInternalServerError());
    }

    @Test
    void updateReturns200WithTheLink() throws Exception {
        when(links.update(ID, DETAILS)).thenReturn(LINK);

        mockMvc.perform(json(put("/api/links/" + ID), BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Router"));
    }

    @Test
    void updateUnknownReturns404() throws Exception {
        when(links.update(any(), any())).thenThrow(new LinkNotFoundException(ID));

        mockMvc.perform(json(put("/api/links/" + ID), BODY)).andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/links/" + ID)).andExpect(status().isNoContent());

        verify(links).delete(ID);
    }

    @Test
    void deleteUnknownReturns404() throws Exception {
        doThrow(new LinkNotFoundException(ID)).when(links).delete(ID);

        mockMvc.perform(delete("/api/links/" + ID)).andExpect(status().isNotFound());
    }

    @Test
    void selectIconReturns204() throws Exception {
        mockMvc.perform(json(put("/api/links/" + ID + "/icon"), "{\"name\":\"router\"}"))
                .andExpect(status().isNoContent());

        verify(links).selectLibraryIcon(ID, "router");
    }

    @Test
    void selectIconUnknownReturns404() throws Exception {
        doThrow(new LinkNotFoundException(ID)).when(links).selectLibraryIcon(ID, "router");

        mockMvc.perform(json(put("/api/links/" + ID + "/icon"), "{\"name\":\"router\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadReturns204() throws Exception {
        mockMvc.perform(upload()).andExpect(status().isNoContent());

        verify(links).uploadIcon(ID, PNG);
    }

    @Test
    void uploadWrongTypeReturns400() throws Exception {
        doThrow(new IllegalArgumentException("bad")).when(links).uploadIcon(ID, PNG);

        mockMvc.perform(upload()).andExpect(status().isBadRequest());
    }

    @Test
    void uploadUnknownReturns404() throws Exception {
        doThrow(new LinkNotFoundException(ID)).when(links).uploadIcon(ID, PNG);

        mockMvc.perform(upload()).andExpect(status().isNotFound());
    }

    @Test
    void uploadTooLargeReturns413() throws Exception {
        doThrow(new MaxUploadSizeExceededException(1)).when(links).uploadIcon(ID, PNG);

        mockMvc.perform(upload()).andExpect(status().isPayloadTooLarge());
    }

    @Test
    void uploadAtLimitReturns507() throws Exception {
        doThrow(new IconLimitReachedException(500)).when(links).uploadIcon(ID, PNG);

        mockMvc.perform(upload()).andExpect(status().isInsufficientStorage());
    }

    @Test
    void uploadMissingFileReturnsGenericMessage() throws Exception {
        mockMvc.perform(multipart("/api/links/" + ID + "/icon/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Upload is missing required multipart fields"));
    }
}
