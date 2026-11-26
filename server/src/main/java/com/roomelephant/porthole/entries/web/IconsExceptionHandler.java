package com.roomelephant.porthole.entries.web;

import com.roomelephant.porthole.entries.IconLimitReachedException;
import com.roomelephant.porthole.entries.IconTooLargeException;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before the global handler, whose catch-all would otherwise answer these with 500. */
@NullMarked
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = IconsExceptionHandler.class)
public class IconsExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(IconsExceptionHandler.class);

    @ExceptionHandler(IconLimitReachedException.class)
    public ProblemDetail storageFull(IconLimitReachedException ex) {
        log.warn(ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INSUFFICIENT_STORAGE, ex.getMessage());
    }

    @ExceptionHandler(IconTooLargeException.class)
    public ProblemDetail tooLarge(IconTooLargeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, ex.getMessage());
    }
}
