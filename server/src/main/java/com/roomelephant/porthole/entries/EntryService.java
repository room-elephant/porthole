package com.roomelephant.porthole.entries;

import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.fleet.SourceCatalog;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * What users saved for containers and links. Reads come from memory; each change is written to the store first and
 * becomes visible only once the write succeeded. Writes run one at a time.
 */
@NullMarked
@Service
public class EntryService {
    public static final int MAX_LINKS = 500;
    private static final Logger log = LoggerFactory.getLogger(EntryService.class);

    private final StateStore store;
    private final IconService icons;
    private final SourceCatalog sources;
    private final List<EntryListener> listeners;
    private volatile EntryState state;

    public EntryService(StateStore store, IconService icons, SourceCatalog sources, List<EntryListener> listeners) {
        this.store = store;
        this.icons = icons;
        this.sources = sources;
        this.listeners = List.copyOf(listeners);
        this.state = store.load();
    }

    public EntryState state() {
        return state;
    }

    public synchronized LinkRef createLink(LinkDraft draft) {
        LinkRef ref = LinkRef.random();
        String url = draft.url();
        EntryRecord record = new EntryRecord(
                ref,
                draft.name(),
                claimed(draft.icon()),
                url == null ? null : new EndpointChoice.Custom(url.trim()),
                draft.stack());
        if (state.linkCount() >= MAX_LINKS) throw new LinkLimitReachedException(MAX_LINKS);
        commit(state.with(record));
        return ref;
    }

    /**
     * @throws EntryNotFoundException for a malformed ID, an unknown link, or a container on an unconfigured source
     * @throws IllegalArgumentException when the patch is not valid for this kind of entry
     */
    public synchronized void update(String id, EntryPatch patch) {
        EntryRef ref = EntryRef.parse(id);
        EntryRecord current = current(ref, id);
        if (ref instanceof ContainerRef && !(patch.stack() instanceof PatchField.Absent))
            throw new IllegalArgumentException("Containers take their stack from Docker Compose");
        EntryRecord updated = new EntryRecord(
                ref,
                apply(patch.name(), current.name()),
                patch.icon() instanceof PatchField.Set<Icon> icon
                        ? claimed(icon.value())
                        : apply(patch.icon(), current.icon()),
                apply(patch.endpoint(), current.endpoint()),
                apply(patch.stack(), current.stack()));
        commit(state.with(updated));
    }

    /**
     * @throws EntryNotDeletableException for a container entry
     * @throws EntryNotFoundException for a malformed ID or an unknown link
     */
    public synchronized void delete(String id) {
        EntryRef ref = EntryRef.parse(id);
        if (ref instanceof ContainerRef) throw new EntryNotDeletableException();
        if (state.find(ref).isEmpty()) throw new EntryNotFoundException(id);
        commit(state.without(ref));
    }

    private EntryRecord current(EntryRef ref, String id) {
        return switch (ref) {
            case ContainerRef container -> {
                if (!sources.contains(container.sourceId())) throw new EntryNotFoundException(id);
                yield state.find(ref).orElseGet(() -> EntryRecord.container(container));
            }
            case LinkRef _ -> state.find(ref).orElseThrow(() -> new EntryNotFoundException(id));
        };
    }

    private static <T> @Nullable T apply(PatchField<T> field, @Nullable T current) {
        return switch (field) {
            case PatchField.Absent<T> _ -> current;
            case PatchField.Removed<T> _ -> null;
            case PatchField.Set<T> set -> set.value();
        };
    }

    /** Makes sure an uploaded icon exists and keeps the sweep away from it while it is being saved. */
    private @Nullable Icon claimed(@Nullable Icon icon) {
        if (icon instanceof Icon.Upload upload && !icons.claim(upload.id()))
            throw new IllegalArgumentException("Unknown icon " + upload.id());
        return icon;
    }

    private void commit(EntryState next) {
        store.save(next);
        state = next;
        for (EntryListener listener : listeners) {
            try {
                listener.onEntriesChanged();
            } catch (RuntimeException e) {
                log.warn("Entry listener failed", e);
            }
        }
    }
}
