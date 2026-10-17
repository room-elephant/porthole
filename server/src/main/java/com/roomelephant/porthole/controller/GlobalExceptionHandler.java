package com.roomelephant.porthole.controller;

import com.roomelephant.porthole.domain.model.exception.DockerUnavailableException;
import com.roomelephant.porthole.domain.model.exception.NotFoundException;
import com.roomelephant.porthole.domain.model.exception.UnexpectedException;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    @ExceptionHandler(DockerUnavailableException.class)
    public ProblemDetail handleDockerUnavailable(DockerUnavailableException ex) {
        log.error("Docker is not reachable", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Docker is not reachable");
        problem.setTitle("Bad Gateway");
        problem.setType(ABOUT_BLANK);
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        log.debug("Container not found: {}", ex.getContainerId());
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Container not found: " + ex.getContainerId());
        problem.setTitle("Not Found");
        problem.setType(ABOUT_BLANK);
        return problem;
    }

    @ExceptionHandler(UnexpectedException.class)
    public ProblemDetail handleUnexpected(UnexpectedException ex) {
        log.error("Failed to inspect container", ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to inspect container");
        problem.setTitle("Internal Server Error");
        problem.setType(ABOUT_BLANK);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(ABOUT_BLANK);
        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleMissingResource(NoResourceFoundException ex) {
        return ex.getBody();
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(ABOUT_BLANK);
        return problem;
    }
}
