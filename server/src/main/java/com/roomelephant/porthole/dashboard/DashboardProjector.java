package com.roomelephant.porthole.dashboard;

import com.roomelephant.porthole.core.enrichment.version.refresh.VersionChecks;
import com.roomelephant.porthole.core.model.SelfContainer;
import com.roomelephant.porthole.endpoints.Candidate;
import com.roomelephant.porthole.endpoints.EndpointDiscovery;
import com.roomelephant.porthole.endpoints.EndpointSelection;
import com.roomelephant.porthole.endpoints.EndpointTarget;
import com.roomelephant.porthole.entries.EndpointChoice;
import com.roomelephant.porthole.entries.EntryRecord;
import com.roomelephant.porthole.entries.EntryRef;
import com.roomelephant.porthole.entries.EntryRef.ContainerRef;
import com.roomelephant.porthole.entries.EntryRef.LinkRef;
import com.roomelephant.porthole.entries.EntryState;
import com.roomelephant.porthole.entries.Icon;
import com.roomelephant.porthole.fleet.ContainerKey;
import com.roomelephant.porthole.fleet.ContainerSnapshot;
import com.roomelephant.porthole.fleet.Source;
import com.roomelephant.porthole.fleet.SourceSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds the dashboard from what Docker reports, what users saved and what version checks found. A pure function of
 * its inputs: it does no I/O and keeps no state.
 */
@NullMarked
@Component
public class DashboardProjector {
    static final String UNKNOWN_IMAGE = "Unknown";
    static final String CUSTOM = "custom";

    private final EndpointDiscovery discovery;
    private final DefaultIcons icons;
    private final SelfContainer self;
    private final DisplayNames displayNames = new DisplayNames();

    public DashboardProjector(EndpointDiscovery discovery, DefaultIcons icons, SelfContainer self) {
        this.discovery = discovery;
        this.icons = icons;
        this.self = self;
    }

    /** Containers by source in configuration and listing order, then links in the order they were saved. */
    public Dashboard project(
            long revision,
            VersionChecks checks,
            List<SourceSnapshot> snapshots,
            EntryState entries,
            VersionLookup versions) {
        List<DashboardEntry> projected = new ArrayList<>();
        for (SourceSnapshot snapshot : snapshots) projected.addAll(containers(snapshot, entries, versions));
        for (EntryRecord record : entries.entries()) {
            if (record.ref() instanceof LinkRef) projected.add(link(record));
        }
        return new Dashboard(revision, snapshots, projected, checks);
    }

    /** The one entry, or empty for an unknown link or a container no snapshot lists. */
    public Optional<DashboardEntry> entry(
            EntryRef ref, List<SourceSnapshot> snapshots, EntryState entries, VersionLookup versions) {
        return switch (ref) {
            case LinkRef _ -> entries.find(ref).map(this::link);
            case ContainerRef container ->
                snapshots.stream()
                        .filter(snapshot -> snapshot.source().id().equals(container.sourceId()))
                        .flatMap(snapshot -> containers(snapshot, entries, versions).stream())
                        .filter(entry -> entry.ref().equals(ref))
                        .findFirst();
        };
    }

    private List<DashboardEntry> containers(SourceSnapshot snapshot, EntryState entries, VersionLookup versions) {
        List<ContainerSnapshot> listed = snapshot.containers().stream()
                .filter(container -> container.id() != null)
                .toList();
        List<String> names = displayNames.resolve(listed);
        List<DashboardEntry> projected = new ArrayList<>(listed.size());
        for (int index = 0; index < listed.size(); index++) {
            projected.add(container(snapshot.source(), listed.get(index), names.get(index), entries, versions));
        }
        return projected;
    }

    private DashboardEntry container(
            Source source,
            ContainerSnapshot container,
            String defaultName,
            EntryState entries,
            VersionLookup versions) {
        String id = Objects.requireNonNull(container.id());
        ContainerRef ref = new ContainerRef(source.id(), container.name());
        @Nullable EntryRecord saved = entries.find(ref).orElse(null);
        @Nullable String savedName = saved == null ? null : saved.name();
        @Nullable Icon savedIcon = saved == null ? null : saved.icon();
        @Nullable EndpointChoice choice = saved == null ? null : saved.endpoint();
        boolean isSelf = self.matches(id, source.local());
        List<Candidate> candidates = discovery.discover(new EndpointTarget(
                container.name(), container.publishedPorts(), container.labels(), source.publishedHost()));
        List<ResolvedEndpoint> endpoints = resolve(candidates, choice);
        String image = container.image() == null ? UNKNOWN_IMAGE : container.image();
        ContainerFacts facts = new ContainerFacts(
                source.id(),
                container.name(),
                defaultName,
                image,
                container.state(),
                container.status(),
                isSelf,
                versions.find(new ContainerKey(source.id(), id)));
        IconSource icon = savedIcon != null
                ? iconSource(savedIcon)
                : isSelf ? new IconSource.Self() : new IconSource.Library(icons.nameFor(container.image()));
        return new DashboardEntry(
                ref,
                savedName != null ? savedName : defaultName,
                DisplayNames.project(container),
                icon,
                savedIcon,
                endpoints,
                hasWebUi(endpoints, choice),
                facts);
    }

    private DashboardEntry link(EntryRecord record) {
        EndpointChoice.Custom url = (EndpointChoice.Custom) Objects.requireNonNull(record.endpoint());
        List<ResolvedEndpoint> endpoints = List.of(new ResolvedEndpoint(CUSTOM, CUSTOM, url.url(), true));
        @Nullable Icon icon = record.icon();
        return new DashboardEntry(
                record.ref(),
                Objects.requireNonNull(record.name()),
                record.stack(),
                icon == null ? new IconSource.None() : iconSource(icon),
                icon,
                endpoints,
                true,
                null);
    }

    /** Applies a saved choice over automatic selection; a saved candidate that disappeared falls back to automatic. */
    private static List<ResolvedEndpoint> resolve(List<Candidate> candidates, @Nullable EndpointChoice choice) {
        int selected = switch (choice) {
            case EndpointChoice.None _, EndpointChoice.Custom _ -> -1;
            case EndpointChoice.Discovered discovered -> {
                int saved = indexOf(candidates, discovered.id());
                yield saved >= 0 ? saved : EndpointSelection.automatic(candidates);
            }
            case null -> EndpointSelection.automatic(candidates);
        };
        List<ResolvedEndpoint> resolved = new ArrayList<>(candidates.size() + 1);
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            resolved.add(new ResolvedEndpoint(candidate.id(), candidate.source(), candidate.url(), index == selected));
        }
        if (choice instanceof EndpointChoice.Custom custom)
            resolved.add(new ResolvedEndpoint(CUSTOM, CUSTOM, custom.url(), true));
        return resolved;
    }

    private static int indexOf(List<Candidate> candidates, String id) {
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).id().equals(id)) return index;
        }
        return -1;
    }

    private static boolean hasWebUi(List<ResolvedEndpoint> endpoints, @Nullable EndpointChoice choice) {
        return !endpoints.isEmpty() && !(choice instanceof EndpointChoice.None);
    }

    private static IconSource iconSource(Icon icon) {
        return switch (icon) {
            case Icon.Library library -> new IconSource.Library(library.name());
            case Icon.Upload upload -> new IconSource.Upload(upload.id());
        };
    }
}
