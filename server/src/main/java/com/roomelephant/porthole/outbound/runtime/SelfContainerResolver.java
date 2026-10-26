package com.roomelephant.porthole.outbound.runtime;

import com.roomelephant.porthole.core.model.SelfContainer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Resolves the ID of the container this process runs in, first hit wins. */
@NullMarked
public final class SelfContainerResolver {
    private static final Pattern MOUNTINFO_ID = Pattern.compile("/containers/([0-9a-f]{64})/");
    private static final Set<String> OWN_MOUNTS = Set.of("/etc/hostname", "/etc/hosts", "/etc/resolv.conf");
    private static final Pattern CGROUP_ID = Pattern.compile("([0-9a-f]{64})");
    private static final Pattern HOSTNAME_ID = Pattern.compile("[0-9a-f]{12,64}");

    private final UnaryOperator<@Nullable String> env;
    private final Path mountinfo;
    private final Path cgroup;

    public SelfContainerResolver(UnaryOperator<@Nullable String> env, Path mountinfo, Path cgroup) {
        this.env = env;
        this.mountinfo = mountinfo;
        this.cgroup = cgroup;
    }

    public SelfContainer resolve(@Nullable String override) {
        if (override != null && !override.isBlank()) {
            return new SelfContainer(override.trim().toLowerCase(Locale.ROOT));
        }
        return findInMountinfo()
                .or(this::findInCgroup)
                .or(this::hostname)
                .map(SelfContainer::new)
                .orElseGet(SelfContainer::none);
    }

    /** Only the container's own /etc mounts count; other mounts may point into a different container's directory. */
    private Optional<String> findInMountinfo() {
        return read(mountinfo)
                .flatMap(content -> content.lines()
                        .map(line -> line.trim().split("\\s+"))
                        .filter(fields -> fields.length > 4 && OWN_MOUNTS.contains(fields[4]))
                        .map(fields -> MOUNTINFO_ID.matcher(String.join(" ", fields)))
                        .filter(Matcher::find)
                        .map(matcher -> matcher.group(1))
                        .findFirst());
    }

    private Optional<String> read(Path file) {
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException | UncheckedIOException | SecurityException _) {
            return Optional.empty();
        }
    }

    private Optional<String> findInCgroup() {
        String content = read(cgroup).orElse(null);
        if (content == null) {
            return Optional.empty();
        }
        Matcher matcher = CGROUP_ID.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Optional<String> hostname() {
        String hostname = env.apply("HOSTNAME");
        return hostname != null && HOSTNAME_ID.matcher(hostname).matches() ? Optional.of(hostname) : Optional.empty();
    }
}
