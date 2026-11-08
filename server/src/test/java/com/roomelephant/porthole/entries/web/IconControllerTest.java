package com.roomelephant.porthole.entries.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roomelephant.porthole.entries.IconLimitReachedException;
import com.roomelephant.porthole.entries.IconService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IconController.class)
class IconControllerTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final String ID = "a".repeat(64) + ".png";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IconService icons;

    @Test
    void uploadReturns201WithId() throws Exception {
        when(icons.upload(PNG)).thenReturn(ID);

        mockMvc.perform(post("/api/icons").contentType(MediaType.IMAGE_PNG).content(PNG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID));
    }

    @Test
    void uploadAcceptsAnyContentType() throws Exception {
        when(icons.upload(PNG)).thenReturn(ID);

        mockMvc.perform(post("/api/icons").contentType(MediaType.TEXT_PLAIN).content(PNG))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/icons").content(PNG)).andExpect(status().isCreated());
    }

    @Test
    void uploadOfUnknownFormatIs400() throws Exception {
        when(icons.upload(any()))
                .thenThrow(new IllegalArgumentException("Icon must be a PNG, WebP, JPEG or SVG image"));

        mockMvc.perform(post("/api/icons").content("hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Icon must be a PNG, WebP, JPEG or SVG image"));
    }

    @Test
    void uploadOverLimitWithContentLengthIs413() throws Exception {
        mockMvc.perform(post("/api/icons")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(new byte[IconService.MAX_BYTES + 1]))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.detail").value("Icon must be at most 1 MB"));

        verify(icons, never()).upload(any());
    }

    @Test
    void chunkedUploadOverLimitIs413() throws Exception {
        mockMvc.perform(post("/api/icons")
                        .contentType(MediaType.IMAGE_PNG)
                        .header("Transfer-Encoding", "chunked")
                        .content(new byte[IconService.MAX_BYTES + 10])
                        .with(request -> {
                            request.removeHeader("Content-Length");
                            return request;
                        }))
                .andExpect(status().isContentTooLarge());

        verify(icons, never()).upload(any());
    }

    @Test
    void fullStorageIs507() throws Exception {
        when(icons.upload(any())).thenThrow(new IconLimitReachedException(500));

        mockMvc.perform(post("/api/icons").content(PNG))
                .andExpect(status().isInsufficientStorage())
                .andExpect(jsonPath("$.detail").value("Porthole has reached its limit of 500 uploaded icons"));
    }
}
