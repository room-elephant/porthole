package com.roomelephant.porthole.outbound.links;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.roomelephant.porthole.core.model.ExternalLink;
import com.roomelephant.porthole.core.model.ExternalLinkDetails;
import com.roomelephant.porthole.core.model.ExternalLinkValidation;
import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.model.exception.LinkNotFoundException;
import com.roomelephant.porthole.core.port.outbound.ExternalLinkStore;
import com.roomelephant.porthole.shared.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Holds links in one JSON file, loaded once at startup. One Porthole instance owns the file. */
@NullMarked
public final class JsonExternalLinkStore implements ExternalLinkStore {
    private final Path path;
    private final JsonMapper mapper;
    private volatile List<ExternalLink> snapshot;

    public JsonExternalLinkStore(Path path, JsonMapper mapper) {
        this.path = path.toAbsolutePath().normalize();
        this.mapper = mapper.rebuild()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        try {
            snapshot = load();
        } catch (IOException | JacksonException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to load external links from " + this.path, e);
        }
    }

    @Override
    public List<ExternalLink> snapshot() {
        return snapshot;
    }

    @Override
    public synchronized void add(ExternalLink link) {
        List<ExternalLink> updated = new ArrayList<>(snapshot);
        updated.add(link);
        save(updated);
    }

    @Override
    public synchronized ExternalLink replaceDetails(String id, ExternalLinkDetails details) {
        return update(id, link -> link.withDetails(details));
    }

    @Override
    public synchronized void replaceIcon(String id, SelectedIcon icon) {
        update(id, link -> link.withIcon(icon));
    }

    @Override
    public synchronized ExternalLink remove(String id) {
        ExternalLink link = find(id);
        List<ExternalLink> updated = new ArrayList<>(snapshot);
        updated.remove(link);
        save(updated);
        return link;
    }

    private ExternalLink update(String id, UnaryOperator<ExternalLink> change) {
        ExternalLink changed = change.apply(find(id));
        save(snapshot.stream()
                .map(link -> link.id().equals(id) ? changed : link)
                .toList());
        return changed;
    }

    private ExternalLink find(String id) {
        return snapshot.stream()
                .filter(link -> link.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new LinkNotFoundException(id));
    }

    private void save(List<ExternalLink> updated) {
        try {
            AtomicFiles.replace(path, mapper.writeValueAsBytes(updated), ".links-");
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Failed to save external links to " + path, e);
        }
        snapshot = List.copyOf(updated);
    }

    private List<ExternalLink> load() throws IOException {
        byte[] contents;
        try {
            contents = Files.readAllBytes(path);
        } catch (NoSuchFileException _) {
            return List.of();
        }
        if (new String(contents, StandardCharsets.UTF_8).isBlank()) {
            return List.of();
        }
        ExternalLink[] stored = mapper.readValue(contents, ExternalLink[].class);
        if (stored == null) {
            throw new IllegalArgumentException("Expected an array of external links");
        }
        Set<String> ids = new HashSet<>();
        for (ExternalLink link : stored) {
            if (link == null) {
                throw new IllegalArgumentException("Incomplete external link record");
            }
            ExternalLinkValidation.validate(link);
            if (!ids.add(link.id())) {
                throw new IllegalArgumentException("Duplicate link id: " + link.id());
            }
        }
        return List.of(stored);
    }
}
