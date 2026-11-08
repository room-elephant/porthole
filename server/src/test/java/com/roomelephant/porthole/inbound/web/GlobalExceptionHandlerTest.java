package com.roomelephant.porthole.inbound.web;

import static org.junit.jupiter.api.Assertions.*;

import com.roomelephant.porthole.core.model.exception.LinkLimitReachedException;
import com.roomelephant.porthole.core.model.exception.LinkNotFoundException;
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

    @Test
    void linkNotFoundIs404() {
        ProblemDetail problem = exceptionHandler.handleLinkNotFound(new LinkNotFoundException("abc"));
        assertEquals(404, problem.getStatus());
    }

    @Test
    void linkLimitIs507() {
        ProblemDetail problem = exceptionHandler.handleLinkLimitReached(new LinkLimitReachedException(500));
        assertEquals(507, problem.getStatus());
    }

    @Test
    void invalidUploadDoesNotNameContainerFields() {
        ProblemDetail problem = exceptionHandler.handleInvalidUploadRequest();
        assertEquals("Upload is missing required multipart fields", problem.getDetail());
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
    @DisplayName("should return 413 for an upload over the size limit")
    void returnsContentTooLargeForOversizedUpload() {
        assertEquals(413, exceptionHandler.handleUploadTooLarge().getStatus());
    }
}
