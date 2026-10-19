package com.roomelephant.porthole.outbound.runtime;

import com.roomelephant.porthole.core.model.SelfContainer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Resolves the ID of the container this process runs in, first hit wins. */
@NullMarked
public final class SelfContainerResolver {
    private static final Pattern MOUNTINFO_ID = Pattern.compile("/containers/([0-9a-f]{64})/");
    private static final Pattern CGROUP_ID = Pattern.compile("([0-9a-f]{64})");
    private static final Pattern HOSTNAME_ID = Pattern.compile("[0-9a-f]{12,64}");

    private final Function<String, @Nullable String> env;
    private final Path mountinfo;
    private final Path cgroup;

    public SelfContainerResolver(Function<String, @Nullable String> env, Path mountinfo, Path cgroup) {
        this.env = env;
        this.mountinfo = mountinfo;
        this.cgroup = cgroup;
    }

    public SelfContainer resolve(@Nullable String override) {
        if (override != null && !override.isBlank()) {
            return new SelfContainer(override.trim().toLowerCase(Locale.ROOT));
        }
        return find(mountinfo, MOUNTINFO_ID)
                .or(() -> find(cgroup, CGROUP_ID))
                .or(this::hostname)
                .map(SelfContainer::new)
                .orElseGet(SelfContainer::none);
    }

    private Optional<String> find(Path file, Pattern pattern) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException | UncheckedIOException | SecurityException _) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Optional<String> hostname() {
        String hostname = env.apply("HOSTNAME");
        return hostname != null && HOSTNAME_ID.matcher(hostname).matches() ? Optional.of(hostname) : Optional.empty();
    }
}
