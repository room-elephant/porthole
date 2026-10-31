package com.roomelephant.porthole.inbound.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;

@NullMarked
@Schema(description = "Confirmed saved preferences for the identified container")
public record SelectContainerEndpointResponse(
        String sourceId, String containerName, ContainerPreferencesDTO preferences) {}
