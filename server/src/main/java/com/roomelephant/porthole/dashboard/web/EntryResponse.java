package com.roomelephant.porthole.dashboard.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.roomelephant.porthole.core.model.VersionInfo;
import com.roomelephant.porthole.dashboard.ContainerFacts;
import com.roomelephant.porthole.dashboard.DashboardEntry;
import com.roomelephant.porthole.dashboard.ResolvedEndpoint;
import com.roomelephant.porthole.entries.EntryRef;
import com.roomelephant.porthole.entries.Icon;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@NullMarked
@RegisterReflectionForBinding({
    EntryResponse.class,
    EntryResponse.IconResponse.class,
    EntryResponse.IconChoice.class,
    EntryResponse.EndpointResponse.class,
    EntryResponse.ContainerResponse.class,
    VersionInfo.class
})
@Schema(description = "A container or link on the dashboard, with saved overrides applied")
public record EntryResponse(
        @Schema(description = "Entry ID, for /api/entries/{id}", example = "container:walker:smh-sonarr-1")
        String id,

        @Schema(
                description = "container or link",
                allowableValues = {"container", "link"})
        String kind,

        @Schema(description = "Name shown on the tile: the saved name, else the container's default name")
        String name,

        @Schema(description = "Compose project for a container, saved stack for a link", nullable = true) @Nullable
        String stack,

        IconResponse icon,

        @Schema(description = "Addresses the tile can open; at most one is selected")
        List<EndpointResponse> endpoints,

        @Schema(description = "False for an explicit no-web-UI choice or when there are no endpoints")
        boolean hasWebUi,

        @Schema(description = "Docker facts; null for a link", nullable = true) @Nullable
        ContainerResponse container) {
    static final String CONTAINER = "container";
    static final String LINK = "link";

    static EntryResponse from(DashboardEntry entry, IconUrls icons) {
        ContainerFacts facts = entry.container();
        return new EntryResponse(
                entry.ref().id(),
                entry.ref() instanceof EntryRef.LinkRef ? LINK : CONTAINER,
                entry.name(),
                entry.stack(),
                new IconResponse(icons.url(entry.icon()), IconChoice.from(entry.iconChoice())),
                entry.endpoints().stream().map(EndpointResponse::from).toList(),
                entry.hasWebUi(),
                facts == null ? null : ContainerResponse.from(facts));
    }

    @Schema(description = "The icon shown, and the saved choice when the user picked one")
    public record IconResponse(
            @Schema(description = "Image URL; null for a link without an icon", nullable = true) @Nullable
            String url,

            @Schema(description = "The saved choice, or null when the default is shown", nullable = true) @Nullable
            IconChoice choice) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Exactly one of library or upload")
    public record IconChoice(
            @Schema(description = "Dashboard Icons library name") @Nullable
            String library,

            @Schema(description = "Uploaded icon ID") @Nullable
            String upload) {
        static @Nullable IconChoice from(@Nullable Icon icon) {
            return switch (icon) {
                case null -> null;
                case Icon.Library library -> new IconChoice(library.name(), null);
                case Icon.Upload upload -> new IconChoice(null, upload.id());
            };
        }
    }

    public record EndpointResponse(String id, String source, String url, boolean selected) {
        static EndpointResponse from(ResolvedEndpoint endpoint) {
            return new EndpointResponse(endpoint.id(), endpoint.source(), endpoint.url(), endpoint.selected());
        }
    }

    public record ContainerResponse(
            String sourceId,
            @Schema(description = "Docker container name") String name,

            @Schema(description = "Name shown when none is saved")
            String defaultName,

            String image,
            @Nullable String state,
            @Nullable String status,

            @Schema(description = "Whether this is the container Porthole runs in")
            boolean self,

            @Schema(description = "Cached version check result; null until checked", nullable = true) @Nullable
            VersionInfo version) {
        static ContainerResponse from(ContainerFacts facts) {
            return new ContainerResponse(
                    facts.sourceId(),
                    facts.name(),
                    facts.defaultName(),
                    facts.image(),
                    facts.state(),
                    facts.status(),
                    facts.self(),
                    facts.version());
        }
    }
}
