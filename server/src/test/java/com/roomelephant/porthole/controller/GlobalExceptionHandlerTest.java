package com.roomelephant.porthole.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.roomelephant.porthole.domain.model.exception.DockerUnavailableException;
import com.roomelephant.porthole.domain.model.exception.NotFoundException;
import com.roomelephant.porthole.domain.model.exception.UnexpectedException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleDockerUnavailable")
    class HandleDockerUnavailable {

        @Test
        @DisplayName("should return ProblemDetail with BAD_GATEWAY status")
        void shouldReturnProblemDetailWithBadGatewayStatus() {
            DockerUnavailableException exception =
                    new DockerUnavailableException(new RuntimeException("Connection refused"));

            ProblemDetail result = exceptionHandler.handleDockerUnavailable(exception);

            assertEquals(502, result.getStatus());
            assertEquals("Docker is not reachable", result.getDetail());
            assertEquals("Bad Gateway", result.getTitle());
            assertEquals(URI.create("about:blank"), result.getType());
        }
    }

    @Nested
    @DisplayName("handleNotFound")
    class HandleNotFound {

        @Test
        @DisplayName("should return ProblemDetail with NOT_FOUND status")
        void shouldReturnProblemDetailWithNotFoundStatus() {
            NotFoundException exception = new NotFoundException("container123");

            ProblemDetail result = exceptionHandler.handleNotFound(exception);

            assertEquals(404, result.getStatus());
            assertEquals("Container not found: container123", result.getDetail());
            assertEquals("Not Found", result.getTitle());
            assertEquals(URI.create("about:blank"), result.getType());
        }
    }

    @Nested
    @DisplayName("handleUnexpected")
    class HandleUnexpected {

        @Test
        @DisplayName("should return ProblemDetail with INTERNAL_SERVER_ERROR status")
        void shouldReturnProblemDetailWithInternalServerErrorStatus() {
            UnexpectedException exception = new UnexpectedException(new RuntimeException("Docker error"));

            ProblemDetail result = exceptionHandler.handleUnexpected(exception);

            assertEquals(500, result.getStatus());
            assertEquals("Failed to inspect container", result.getDetail());
            assertEquals("Internal Server Error", result.getTitle());
            assertEquals(URI.create("about:blank"), result.getType());
        }
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
}
