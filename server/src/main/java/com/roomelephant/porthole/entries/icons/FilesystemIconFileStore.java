package com.roomelephant.porthole.entries.icons;

import com.roomelephant.porthole.entries.Icon;
import com.roomelephant.porthole.entries.IconFileStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

/** Keeps each uploaded icon as a file named by its ID. Only names matching the upload ID pattern are ever touched. */
@NullMarked
public final class FilesystemIconFileStore implements IconFileStore {
    private static final String TEMPORARY_PREFIX = ".icon-";
    private static final String TEMPORARY_SUFFIX = ".tmp";

    private final Path directory;
    private final Clock clock;

    public FilesystemIconFileStore(Path directory, Clock clock) {
        this.directory = directory.toAbsolutePath().normalize();
        this.clock = clock;
    }

    // Same ID means same bytes, so a concurrent store of one ID can safely replace the other.
    @Override
    public boolean store(String id, byte[] bytes) {
        Path target = resolve(id).orElseThrow(() -> new IllegalArgumentException("Invalid uploaded icon id"));
        if (Files.exists(target)) return false;
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, TEMPORARY_PREFIX, TEMPORARY_SUFFIX);
            try {
                Files.write(temporary, bytes);
                Files.setLastModifiedTime(temporary, FileTime.from(clock.instant()));
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (IOException e) {
                Files.deleteIfExists(temporary);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded icon", e);
        }
    }

    @Override
    public boolean touch(String id) {
        Optional<Path> path = resolve(id);
        if (path.isEmpty() || !Files.isRegularFile(path.get())) return false;
        try {
            Files.setLastModifiedTime(path.get(), FileTime.from(clock.instant()));
            return true;
        } catch (NoSuchFileException _) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to update uploaded icon", e);
        }
    }

    @Override
    public Optional<byte[]> read(String id) {
        Optional<Path> path = resolve(id);
        if (path.isEmpty() || !Files.isRegularFile(path.get())) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(path.get()));
        } catch (NoSuchFileException _) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded icon", e);
        }
    }

    @Override
    public int count() {
        return (int) regularFiles().stream()
                .filter(path -> isUpload(path.getFileName().toString()))
                .count();
    }

    @Override
    public int deleteUnreferenced(Set<String> keep, Instant modifiedBefore) {
        int deleted = 0;
        try {
            for (Path path : regularFiles()) {
                String name = path.getFileName().toString();
                if (!isOwnFile(name) || keep.contains(name)) continue;
                if (!Files.getLastModifiedTime(path).toInstant().isBefore(modifiedBefore)) continue;
                if (Files.deleteIfExists(path)) deleted++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to remove unreferenced icons", e);
        }
        return deleted;
    }

    private Optional<Path> resolve(String id) {
        if (!isUpload(id)) return Optional.empty();
        Path path = directory.resolve(id).normalize();
        return path.getParent().equals(directory) ? Optional.of(path) : Optional.empty();
    }

    private static boolean isUpload(String name) {
        return Icon.UPLOAD_ID.matcher(name).matches();
    }

    private static boolean isOwnFile(String name) {
        return isUpload(name) || (name.startsWith(TEMPORARY_PREFIX) && name.endsWith(TEMPORARY_SUFFIX));
    }

    private List<Path> regularFiles() {
        if (Files.notExists(directory)) return List.of();
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list uploaded icons", e);
        }
    }
}
