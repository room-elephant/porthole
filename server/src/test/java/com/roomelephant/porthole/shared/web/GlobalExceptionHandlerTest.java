package com.roomelephant.porthole.shared.web;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleIllegalArgument")
    class HandleIllegalArgument {

        @Test
        @DisplayName("should return ProblemDetail with BAD_REQUEST status")
        void shouldReturnProblemDetailWithBadRequestStatus() {
            IllegalArgumentException exception = new IllegalArgumentException("Invalid parameter");

            ProblemDetail result = exceptionHandler.handleIllegalArgument(exception);

            assertEquals(400, result.getStatus());
            assertEquals("Invalid parameter", result.getDetail());
            assertEquals("Bad Request", result.getTitle());
            assertEquals(URI.create("about:blank"), result.getType());
        }

        @Test
        @DisplayName("should handle empty message")
        void shouldHandleEmptyMessage() {
            IllegalArgumentException exception = new IllegalArgumentException("");

            ProblemDetail result = exceptionHandler.handleIllegalArgument(exception);

            assertEquals(400, result.getStatus());
            assertEquals("", result.getDetail());
        }
    }

    @Nested
    @DisplayName("handleGenericException")
    class HandleGenericException {

        @Test
        @DisplayName("should return ProblemDetail with INTERNAL_SERVER_ERROR status")
        void shouldReturnProblemDetailWithInternalServerErrorStatus() {
            Exception exception = new Exception("Something went wrong");

            ProblemDetail result = exceptionHandler.handleGenericException(exception);

            assertEquals(500, result.getStatus());
            assertEquals("An unexpected error occurred", result.getDetail());
            assertEquals("Internal Server Error", result.getTitle());
            assertEquals(URI.create("about:blank"), result.getType());
        }

        @Test
        @DisplayName("should not expose internal exception message")
        void shouldNotExposeInternalExceptionMessage() {
            Exception exception = new Exception("Database connection failed with password=secret123");

            ProblemDetail result = exceptionHandler.handleGenericException(exception);

            assertEquals("An unexpected error occurred", result.getDetail());
            assertFalse(result.getDetail().contains("password"));
        }

        @Test
        @DisplayName("should handle NullPointerException")
        void shouldHandleNullPointerException() {
            NullPointerException exception = new NullPointerException("Object was null");

            ProblemDetail result = exceptionHandler.handleGenericException(exception);

            assertEquals(500, result.getStatus());
            assertEquals("An unexpected error occurred", result.getDetail());
        }
    }

    @Test
    void springRequestErrorsKeepTheirStatusAndHeaders() {
        var ex = new org.springframework.web.HttpRequestMethodNotSupportedException("POST", java.util.List.of("GET"));

        var response = exceptionHandler.handleRequestError(ex);

        assertEquals(405, response.getStatusCode().value());
        assertEquals("GET", response.getHeaders().getFirst("Allow"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void disconnectedEventStreamClientIsNeitherAnErrorNorAnswered(CapturedOutput output) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DisconnectingStream())
                .setControllerAdvice(exceptionHandler)
                .build();

        mvc.perform(MockMvcRequestBuilders.get("/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(MockMvcResultMatchers.content().string(""));

        assertFalse(output.getOut().contains("Unexpected error"), output.getOut());
        assertFalse(output.getOut().contains("No converter"), output.getOut());
    }

    @RestController
    static class DisconnectingStream {
        @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        String stream() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("Servlet container error notification for disconnected client");
        }
    }
}
