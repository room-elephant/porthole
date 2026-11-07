package com.roomelephant.porthole.outbound.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Replaces a whole file so a crash leaves either the old contents or the new ones. */
@NullMarked
public final class AtomicFiles {
    private AtomicFiles() {}

    public static void replace(Path target, byte[] bytes, String tempPrefix) throws IOException {
        @Nullable Path temporary = null;
        try {
            Path parent = Objects.requireNonNull(target.getParent());
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, tempPrefix, ".tmp");
            writeDurably(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (FileSystemException moveFailure) {
                if (!Files.isRegularFile(target)) {
                    throw moveFailure;
                }
                writeDurably(target, bytes);
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw e;
        }
    }

    private static void writeDurably(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                target, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }
}
