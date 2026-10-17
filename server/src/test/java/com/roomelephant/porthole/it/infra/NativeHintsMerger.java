package com.roomelephant.porthole.it.infra;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;

/** Generation-profile entry point; runs in a separate JVM after the IT fork exits. */
public final class NativeHintsMerger {
    private static final String METADATA = "reachability-metadata.json";
    private static final String TOOL_IMAGE = "ghcr.io/graalvm/native-image-community:25";

    private NativeHintsMerger() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 2 && arguments[0].equals("prepare")) {
            prepare(Path.of(arguments[1]));
        } else if (arguments.length == 3 && arguments[0].equals("merge")) {
            merge(Path.of(arguments[1]), Path.of(arguments[2]));
        } else {
            throw new IllegalArgumentException("Expected prepare <recordings> or merge <recordings> <output>");
        }
    }

    static void prepare(Path recordings) throws Exception {
        if (Files.exists(recordings)) {
            List<Path> previous;
            try (Stream<Path> paths = Files.walk(recordings)) {
                previous = paths.sorted(Comparator.reverseOrder()).toList();
            }
            if (previous.stream().anyMatch(path -> path.getFileName().toString().equals(".lock"))) {
                throw new IllegalStateException("Previous native hint recordings are still locked: " + recordings);
            }
            for (Path path : previous) Files.delete(path);
        }
        Files.createDirectories(recordings);
    }

    static void merge(Path recordings, Path output) throws Exception {
        List<Path> inputs;
        try (Stream<Path> paths = Files.list(recordings)) {
            inputs = paths.filter(Files::isDirectory).sorted().toList();
        }
        if (inputs.isEmpty()) throw new IllegalStateException("No native hint recordings in " + recordings);
        for (Path input : inputs) {
            if (Files.exists(input.resolve(".lock"))) {
                throw new IllegalStateException("Still recording native hints: " + input);
            }
            if (!Files.isRegularFile(input.resolve(METADATA))) {
                throw new IllegalStateException("Incomplete recording: " + input);
            }
        }

        Path merged = Files.createTempDirectory(recordings.toAbsolutePath().getParent(), "native-hints-merged-");
        List<String> command = new ArrayList<>(List.of("generate"));
        for (Path input : inputs) command.add("--input-dir=/recordings/" + input.getFileName());
        command.add("--output-dir=/merged");
        try (GenericContainer<?> tool = new GenericContainer<>(TOOL_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("native-image-configure"))
                .withFileSystemBind(recordings.toAbsolutePath().toString(), "/recordings", BindMode.READ_ONLY)
                .withFileSystemBind(merged.toString(), "/merged", BindMode.READ_WRITE)
                .withCommand(command.toArray(String[]::new))
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))) {
            tool.start();
        }
        if (!Files.isRegularFile(merged.resolve(METADATA))) {
            throw new IllegalStateException("GraalVM did not produce merged metadata in " + merged);
        }
        Files.createDirectories(output);
        Path staged = Files.createTempFile(output, ".reachability-metadata-", ".json");
        Files.copy(merged.resolve(METADATA), staged, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(
                    staged,
                    output.resolve(METADATA),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(staged, output.resolve(METADATA), StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Merged native hints from " + inputs.size() + " application instances into " + output);
    }
}
