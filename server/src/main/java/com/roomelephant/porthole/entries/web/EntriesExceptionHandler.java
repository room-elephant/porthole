package com.roomelephant.porthole.entries.web;

import com.roomelephant.porthole.entries.EntryNotDeletableException;
import com.roomelephant.porthole.entries.EntryNotFoundException;
import com.roomelephant.porthole.entries.IconLimitReachedException;
import com.roomelephant.porthole.entries.IconTooLargeException;
import com.roomelephant.porthole.entries.LinkLimitReachedException;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before the global handler, whose catch-all would otherwise answer these with 500. */
@NullMarked
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = EntriesExceptionHandler.class)
public class EntriesExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(EntriesExceptionHandler.class);

    @ExceptionHandler(EntryNotFoundException.class)
    public ProblemDetail notFound(EntryNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EntryNotDeletableException.class)
    public ResponseEntity<ProblemDetail> notDeletable(EntryNotDeletableException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, "PATCH")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage()));
    }

    @ExceptionHandler({LinkLimitReachedException.class, IconLimitReachedException.class})
    public ProblemDetail storageFull(RuntimeException ex) {
        log.warn(ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INSUFFICIENT_STORAGE, ex.getMessage());
    }

    @ExceptionHandler(IconTooLargeException.class)
    public ProblemDetail tooLarge(IconTooLargeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, ex.getMessage());
    }
}
