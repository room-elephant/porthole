package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.enrichment.preferences.CustomIconResolver;
import com.roomelephant.porthole.core.model.PresentedLink;
import com.roomelephant.porthole.core.port.inbound.ListLinks;
import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.EntryService;
import com.roomelephant.porthole.entries.Icon;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Presents saved link entries with their icons resolved to URLs. */
@NullMarked
@Service
public class LinkListService implements ListLinks {
    private final EntryService entries;
    private final CustomIconResolver icons;

    public LinkListService(EntryService entries, CustomIconResolver icons) {
        this.entries = entries;
        this.icons = icons;
    }

    @Override
    public List<PresentedLink> list() {
        return entries.state().entries().stream()
                .filter(entry -> entry.ref() instanceof LinkRef)
                .map(this::present)
                .toList();
    }

    private PresentedLink present(EntryRecord link) {
        EndpointChoice.Custom endpoint = (EndpointChoice.Custom) Objects.requireNonNull(link.endpoint());
        @Nullable Icon icon = link.icon();
        return new PresentedLink(
                link.ref().id(),
                Objects.requireNonNull(link.name()),
                endpoint.url(),
                link.stack(),
                icon == null ? null : icons.resolve(icon));
    }
}
