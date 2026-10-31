package com.roomelephant.porthole.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding({
    SelectContainerEndpointRequest.class,
    SelectContainerEndpointResponse.class,
    ContainerPreferencesDTO.class,
    SelectedEndpointDTO.class
})
@Schema(description = "Replace the selected endpoint for a container, including temporarily unavailable containers")
public record SelectContainerEndpointRequest(
        @Schema(description = "Docker connection source ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceId,

        @Schema(
                description = "Docker container name, independent of runtime ID or display name",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String containerName,

        @JsonProperty(value = "preferences", required = true)
        ContainerPreferencesDTO preferences) {
    public SelectContainerEndpointRequest {
        if (preferences == null) {
            throw new IllegalArgumentException("preferences must be provided");
        }
    }
}
