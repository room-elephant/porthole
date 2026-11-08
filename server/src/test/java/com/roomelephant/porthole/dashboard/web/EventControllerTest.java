package com.roomelephant.porthole.dashboard.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(EventController.class)
class EventControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventStreams streams;

    @Test
    void streamsChangeEvents() throws Exception {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.send(SseEmitter.event().name("changed").data("{\"revision\":7}"));
        when(streams.open()).thenReturn(Optional.of(emitter));

        mockMvc.perform(get("/api/events"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream"))
                .andExpect(content().string("event:changed\ndata:{\"revision\":7}\n\n"));
    }

    @Test
    void refusesWhenNoStreamIsAvailable() throws Exception {
        when(streams.open()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/events")).andExpect(status().isServiceUnavailable());
    }
}
