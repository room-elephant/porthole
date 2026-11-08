package com.roomelephant.porthole.outbound.icons;

import com.roomelephant.porthole.core.model.SelectedIcon;
import com.roomelephant.porthole.core.port.outbound.IconFileStore;
import com.roomelephant.porthole.entries.IconImageType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class FilesystemIconFileStore implements IconFileStore {
    private static final String TEMPORARY_PREFIX = ".icon-";
    private static final String TEMPORARY_SUFFIX = ".tmp";

    private final Path directory;

    public FilesystemIconFileStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] bytes, IconImageType type) {
        String name = UUID.randomUUID().toString().replace("-", "") + "." + type.extension();
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, TEMPORARY_PREFIX, TEMPORARY_SUFFIX);
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(temporary);
                throw e;
            }
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded icon", e);
        }
    }

    @Override
    public Optional<byte[]> read(String name) {
        if (!SelectedIcon.UPLOAD_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded icon", e);
        }
    }

    @Override
    public void delete(String name) {
        if (!SelectedIcon.UPLOAD_NAME.matcher(name).matches()) {
            return;
        }
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete uploaded icon", e);
        }
    }

    @Override
    public int count() {
        return (int) regularFiles().stream()
                .filter(path -> SelectedIcon.UPLOAD_NAME
                        .matcher(path.getFileName().toString())
                        .matches())
                .count();
    }

    @Override
    public void retainOnly(Set<String> names) {
        try {
            for (Path path : regularFiles()) {
                String name = path.getFileName().toString();
                if (isOwnFile(name) && !names.contains(name)) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to remove unreferenced icons", e);
        }
    }

    private static boolean isOwnFile(String name) {
        return SelectedIcon.UPLOAD_NAME.matcher(name).matches()
                || (name.startsWith(TEMPORARY_PREFIX) && name.endsWith(TEMPORARY_SUFFIX));
    }

    private List<Path> regularFiles() {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list uploaded icons", e);
        }
    }
}
