package com.roomelephant.porthole.inbound.web.dto;

import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding(EndpointDTO.class)
public record EndpointDTO(String id, String source, String url) {}
