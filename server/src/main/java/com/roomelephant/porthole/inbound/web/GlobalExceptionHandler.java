package com.roomelephant.porthole.inbound.web;

import com.roomelephant.porthole.core.model.exception.DockerUnavailableException;
import com.roomelephant.porthole.core.model.exception.IconLimitReachedException;
import com.roomelephant.porthole.core.model.exception.LinkLimitReachedException;
import com.roomelephant.porthole.core.model.exception.LinkNotFoundException;
import com.roomelephant.porthole.core.model.exception.NotFoundException;
import com.roomelephant.porthole.core.model.exception.UnexpectedException;
import java.net.URI;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI ABOUT_BLANK = URI.create("about:blank");

    @ExceptionHandler(DockerUnavailableException.class)
    public @NonNull ProblemDetail handleDockerUnavailable(DockerUnavailableException ex) {
        log.error("Docker is not reachable", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Docker is not reachable");
        problem.setTitle("Bad Gateway");
        problem.setType(ABOUT_BLANK);
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    public @NonNull ProblemDetail handleNotFound(@NonNull NotFoundException ex) {
        log.debug("Container not found: {}", ex.getContainerId());
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Container not found: " + ex.getContainerId());
        problem.setTitle("Not Found");
        problem.setType(ABOUT_BLANK);
        return problem;
    }

    @ExceptionHandler(UnexpectedException.class)
    public @NonNull ProblemDetail handleUnexpected(UnexpectedException ex) {
        log.error("Failed to inspect container", ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to inspect container");
        problem.setTitle("Internal Server Error");
        problem.setType(ABOUT_BLANK);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public @NonNull ProblemDetail handleIllegalArgument(@NonNull IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(ABOUT_BLANK);
        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public @NonNull ProblemDetail handleMissingResource(@NonNull NoResourceFoundException ex) {
        return ex.getBody();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public @NonNull ProblemDetail handleUnreadableRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public @NonNull ProblemDetail handleUploadTooLarge() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Icon must be at most 1 MB");
    }

    @ExceptionHandler(IconLimitReachedException.class)
    public @NonNull ProblemDetail handleIconLimitReached(@NonNull IconLimitReachedException ex) {
        log.warn(ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INSUFFICIENT_STORAGE, ex.getMessage());
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public @NonNull ProblemDetail handleLinkNotFound(LinkNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LinkLimitReachedException.class)
    public @NonNull ProblemDetail handleLinkLimitReached(LinkLimitReachedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INSUFFICIENT_STORAGE, ex.getMessage());
    }

    @ExceptionHandler({
        MissingServletRequestPartException.class,
        MissingServletRequestParameterException.class,
        MultipartException.class,
        HttpMediaTypeNotSupportedException.class
    })
    public @NonNull ProblemDetail handleInvalidUploadRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Upload is missing required multipart fields");
    }

    @ExceptionHandler(Exception.class)
    public @NonNull ProblemDetail handleGenericException(@NonNull Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(ABOUT_BLANK);
        return problem;
    }
}
