package com.roomelephant.porthole;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.roomelephant.porthole.fleet.DockerOperations;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthProbesTest {
    @TempDir
    static Path data;

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        registry.add("porthole.data.dir", data::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DockerOperations docker;

    @Test
    void healthHasNoDockerComponentAndLivenessIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("docker"))));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/docker")).andExpect(status().isNotFound());
    }
}
