package com.roomelephant.porthole.domain.model;

import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(EndpointDTO.class)
public record EndpointDTO(String id, String source, String url) {}
