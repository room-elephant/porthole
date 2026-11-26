package com.roomelephant.porthole.entries.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roomelephant.porthole.entries.IconLimitReachedException;
import com.roomelephant.porthole.entries.IconService;
import com.roomelephant.porthole.entries.IconTooLargeException;
import com.roomelephant.porthole.entries.StoredIcon;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Optional;
import org.hamcrest.Matchers;
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
        // MockMvc always knows the body length, so call the handler with a request that doesn't, as chunked ones do.
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(-1L);
        byte[] body = new byte[IconService.MAX_BYTES + 10];
        when(request.getInputStream()).thenReturn(new StreamOf(body));

        assertThatThrownBy(() -> new IconController(icons).upload(request)).isInstanceOf(IconTooLargeException.class);

        verify(icons, never()).upload(any());
    }

    @Test
    void chunkedUploadWithinLimitIsStored() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getInputStream()).thenReturn(new StreamOf(PNG));
        when(icons.upload(PNG)).thenReturn(ID);

        assertThat(new IconController(icons).upload(request).id()).isEqualTo(ID);
    }

    private static final class StreamOf extends ServletInputStream {
        private final ByteArrayInputStream bytes;

        private StreamOf(byte[] content) {
            bytes = new ByteArrayInputStream(content);
        }

        @Override
        public int read() {
            return bytes.read();
        }

        @Override
        public boolean isFinished() {
            return bytes.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void fullStorageIs507() throws Exception {
        when(icons.upload(any())).thenThrow(new IconLimitReachedException(500));

        mockMvc.perform(post("/api/icons").content(PNG))
                .andExpect(status().isInsufficientStorage())
                .andExpect(jsonPath("$.detail").value("Porthole has reached its limit of 500 uploaded icons"));
    }

    @Test
    void getReturnsImageWithHeaders() throws Exception {
        when(icons.read(ID)).thenReturn(Optional.of(new StoredIcon(PNG, "image/png")));

        mockMvc.perform(get("/api/icons/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("sandbox")))
                .andExpect(header().string("Cache-Control", Matchers.containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", Matchers.containsString("immutable")));
    }

    @Test
    void getServesSvgAsAnImage() throws Exception {
        String svg = "b".repeat(64) + ".svg";
        when(icons.read(svg)).thenReturn(Optional.of(new StoredIcon("<svg/>".getBytes(), "image/svg+xml")));

        mockMvc.perform(get("/api/icons/{id}", svg))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"));
    }

    @Test
    void getUnknownIs404() throws Exception {
        when(icons.read(ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/icons/{id}", ID)).andExpect(status().isNotFound());
    }

    @Test
    void getInvalidIdIs404() throws Exception {
        when(icons.read(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/icons/{id}", "a".repeat(32) + ".png")).andExpect(status().isNotFound());
        mockMvc.perform(get(URI.create("/api/icons/..%2Fstate.json"))).andExpect(status().isNotFound());
    }
}
