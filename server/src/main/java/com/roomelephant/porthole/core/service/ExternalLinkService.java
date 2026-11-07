package com.roomelephant.porthole.core.service;

import com.roomelephant.porthole.core.enrichment.preferences.CustomIconResolver;
import com.roomelephant.porthole.core.model.ExternalLink;
import com.roomelephant.porthole.core.model.ExternalLinkDetails;
import com.roomelephant.porthole.core.model.ExternalLinkValidation;
import com.roomelephant.porthole.core.model.PresentedLink;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.model.exception.LinkLimitReachedException;
import com.roomelephant.porthole.core.model.exception.LinkNotFoundException;
import com.roomelephant.porthole.core.port.inbound.ManageLinks;
import com.roomelephant.porthole.core.port.outbound.ExternalLinkStore;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@NullMarked
@Service
public class ExternalLinkService implements ManageLinks {
    static final int MAX_LINKS = 500;

    private final ExternalLinkStore store;
    private final UploadedIcons uploads;
    private final CustomIconResolver icons;

    public ExternalLinkService(ExternalLinkStore store, UploadedIcons uploads, CustomIconResolver icons) {
        this.store = store;
        this.uploads = uploads;
        this.icons = icons;
    }

    @Override
    public List<PresentedLink> list() {
        return store.snapshot().stream().map(this::present).toList();
    }

    @Override
    public synchronized PresentedLink create(ExternalLinkDetails details) {
        ExternalLinkDetails valid = ExternalLinkValidation.normalize(details);
        if (store.snapshot().size() >= MAX_LINKS) {
            throw new LinkLimitReachedException(MAX_LINKS);
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        ExternalLink link = new ExternalLink(
                id, Objects.requireNonNull(valid.name()), Objects.requireNonNull(valid.url()), valid.stack(), null);
        store.add(link);
        return present(link);
    }

    @Override
    public PresentedLink update(String id, ExternalLinkDetails details) {
        return present(store.replaceDetails(id, ExternalLinkValidation.normalize(details)));
    }

    @Override
    public synchronized void delete(String id) {
        uploads.deleteQuietly(store.remove(id).icon());
    }

    @Override
    public void selectLibraryIcon(String id, String name) {
        SelectedIcon icon = SelectedIcon.library(name);
        icon.requireValid();
        uploads.saveLibrary(icon, () -> currentIcon(id), persist(id));
    }

    @Override
    public void uploadIcon(String id, byte[] bytes) {
        uploads.saveUpload(bytes, () -> currentIcon(id), persist(id));
    }

    private Consumer<SelectedIcon> persist(String id) {
        return icon -> store.replaceIcon(id, icon);
    }

    private @Nullable SelectedIcon currentIcon(String id) {
        return store.snapshot().stream()
                .filter(link -> link.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new LinkNotFoundException(id))
                .icon();
    }

    private PresentedLink present(ExternalLink link) {
        @Nullable SelectedIcon icon = link.icon();
        return new PresentedLink(
                link.id(), link.name(), link.url(), link.stack(), icon == null ? null : icons.resolve(icon));
    }
}
