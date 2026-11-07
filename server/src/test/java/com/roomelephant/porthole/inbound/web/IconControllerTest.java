package com.roomelephant.porthole.inbound.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roomelephant.porthole.core.model.ContainerPreferencesKey;
import com.roomelephant.porthole.core.model.DockerSource;
import com.roomelephant.porthole.core.model.IconImageType;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.port.outbound.ContainerPreferencesStore;
import com.roomelephant.porthole.core.port.outbound.DockerOperations;
import com.roomelephant.porthole.core.port.outbound.IconFileStore;
import com.roomelephant.porthole.core.service.ContainerIconService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IconController.class)
@Import(ContainerIconService.class)
class IconControllerTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
    private static final String STORED = "0123456789abcdef0123456789abcdef.png";

    private final ContainerPreferencesKey key = new ContainerPreferencesKey("walker", "app");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContainerPreferencesStore preferences;

    @MockitoBean
    private DockerOperations docker;

    @MockitoBean
    private IconFileStore files;

    @BeforeEach
    void setUp() {
        when(docker.sources())
                .thenReturn(List.of(new DockerSource(
                        "walker", "Walker", null, Duration.ofSeconds(1), Duration.ofSeconds(1), false)));
        when(preferences.snapshot()).thenReturn(Map.of());
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "logo.png", "image/png", bytes);
    }

    @Test
    void putIconReturns204AndSavesIt() throws Exception {
        mockMvc.perform(put("/api/containers/icon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"walker\",\"containerName\":\"app\",\"name\":\"coredns\"}"))
                .andExpect(status().isNoContent());
        verify(preferences).replaceIcon(key, SelectedIcon.library("coredns"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"sourceId\":\"walker\",\"containerName\":\"app\",\"name\":\"\"}",
                "{\"sourceId\":\"walker\",\"containerName\":\"app\",\"name\":\"../x\"}",
                "{\"sourceId\":\"walker\",\"containerName\":\"app\"}",
                "{\"sourceId\":\"walker\",\"name\":\"coredns\"}",
                "{\"sourceId\":\"elsewhere\",\"containerName\":\"app\",\"name\":\"coredns\"}",
                "not json"
            })
    void putIconWithInvalidBodyReturns400(String body) throws Exception {
        mockMvc.perform(put("/api/containers/icon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verify(preferences, never()).replaceIcon(any(), any());
    }

    @Test
    void uploadReturns204AndStoresBytes() throws Exception {
        when(files.store(PNG, IconImageType.PNG)).thenReturn(STORED);
        mockMvc.perform(multipart("/api/containers/icon/upload")
                        .file(file(PNG))
                        .param("sourceId", "walker")
                        .param("containerName", "app"))
                .andExpect(status().isNoContent());
        verify(preferences).replaceIcon(key, SelectedIcon.upload(STORED));
    }

    @Test
    void uploadWithWrongFileTypeReturns400() throws Exception {
        mockMvc.perform(multipart("/api/containers/icon/upload")
                        .file(file("<svg/>".getBytes()))
                        .param("sourceId", "walker")
                        .param("containerName", "app"))
                .andExpect(status().isBadRequest());
        verify(files, never()).store(any(), any());
    }

    @Test
    void uploadMissingFilePartReturns400() throws Exception {
        mockMvc.perform(multipart("/api/containers/icon/upload")
                        .param("sourceId", "walker")
                        .param("containerName", "app"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadMissingSourceIdReturns400() throws Exception {
        mockMvc.perform(multipart("/api/containers/icon/upload").file(file(PNG)).param("containerName", "app"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadThatIsNotMultipartReturns400() throws Exception {
        mockMvc.perform(post("/api/containers/icon/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAtLimitReturns507() throws Exception {
        when(files.count()).thenReturn(500);
        mockMvc.perform(multipart("/api/containers/icon/upload")
                        .file(file(PNG))
                        .param("sourceId", "walker")
                        .param("containerName", "app"))
                .andExpect(status().isInsufficientStorage());
        verify(files, never()).store(any(), any());
    }

    @Test
    void persistFailureReturns500() throws Exception {
        when(preferences.replaceIcon(any(), any())).thenThrow(new IllegalStateException("disk full"));
        mockMvc.perform(put("/api/containers/icon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"walker\",\"containerName\":\"app\",\"name\":\"coredns\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getIconReturnsBytesAndHeaders() throws Exception {
        String name = "0123456789abcdef0123456789abcdef.webp";
        when(files.read(name)).thenReturn(Optional.of(PNG));
        mockMvc.perform(get("/api/icons/" + name))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("sandbox")))
                .andExpect(header().string("Cache-Control", Matchers.containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", Matchers.containsString("immutable")));
    }

    @Test
    void getIconServesSvgAsAnImage() throws Exception {
        String name = "0123456789abcdef0123456789abcdef.svg";
        when(files.read(name)).thenReturn(Optional.of(PNG));
        mockMvc.perform(get("/api/icons/" + name))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"));
    }

    @ParameterizedTest
    @ValueSource(strings = {STORED, "abc.svg", "preferences.json"})
    void getIconUnknownReturns404(String name) throws Exception {
        when(files.read(STORED)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/icons/" + name)).andExpect(status().isNotFound());
    }
}
