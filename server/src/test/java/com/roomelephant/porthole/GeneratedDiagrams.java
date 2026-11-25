package com.roomelephant.porthole;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.code.NoPlantumlCompressionException;
import net.sourceforge.plantuml.code.TranscoderUtil;

/**
 * Writes the generated architecture files under one root, only when their content differs, and renders each
 * {@code .puml} to a sibling {@code .svg}. A run that wrote anything fails in {@link #assertNothingChanged()}, so a
 * stale generated file is committed by a person who has seen it.
 */
final class GeneratedDiagrams {

    /** Smetana is PlantUML's Java port of Graphviz's dot, so rendering needs no Graphviz install. */
    private static final String PRAGMA = "!pragma layout smetana";

    private static final Pattern EMBEDDED_SOURCE = Pattern.compile("<\\?plantuml-src ([^?]*)\\?>");

    private final Path root;
    private final List<String> changed = new ArrayList<>();
    // Every file this run wrote or rendered, current or not, so prune can tell a diagram no view produces any more.
    private final Set<Path> produced = new HashSet<>();

    GeneratedDiagrams(Path root) {
        this.root = root;
    }

    /**
     * The repository root: the first directory up from the working directory that holds {@code .git}, a folder in a
     * clone and a file in a worktree, so the tests find the repository whichever directory Maven or the IDE runs
     * them from.
     */
    static Path projectRoot() {
        for (var dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(".git"))) {
                return dir;
            }
        }
        throw new IllegalStateException("No directory above " + Path.of("").toAbsolutePath() + " holds .git, so the "
                + "repository root, and the diagram in it, cannot be found.");
    }

    /** Writes the file when it is missing or differs, and records its path. */
    void write(String relativePath, String content) {
        var file = root.resolve(relativePath);
        produced.add(file.normalize());
        try {
            if (Files.exists(file)
                    && Files.readString(file, StandardCharsets.UTF_8).equals(content)) {
                return;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Writing " + file + " failed.", e);
        }
        changed.add(relativePath);
    }

    /**
     * Renders the sibling {@code .svg} of a {@code .puml} when it is missing or was drawn from other source. PlantUML
     * embeds the source it drew in the SVG, so a current file is told without drawing, and fonts that differ between
     * machines never make it stale.
     */
    void render(String pumlRelativePath) {
        render(pumlRelativePath, pumlRelativePath.replaceFirst("\\.puml$", ".svg"));
    }

    /**
     * Renders a {@code .puml} to the given {@code .svg}, for a hand-written source whose picture lives apart from it,
     * under the same rules as {@link #render(String)}.
     */
    void render(String pumlRelativePath, String svgRelativePath) {
        var puml = root.resolve(pumlRelativePath);
        var svg = root.resolve(svgRelativePath);
        produced.add(svg.normalize());
        try {
            var source = withPragma(puml, Files.readString(puml, StandardCharsets.UTF_8));
            if (Files.exists(svg)
                    && normalised(source).equals(embeddedSource(Files.readString(svg, StandardCharsets.UTF_8)))) {
                return;
            }
            var out = new ByteArrayOutputStream();
            var description = new SourceStringReader(source).outputImage(out, new FileFormatOption(FileFormat.SVG));
            var image = description.getImageData();
            if (image == null || image.getStatus() != 0) {
                throw new IllegalStateException("PlantUML could not draw " + puml + ": " + description
                        + (image == null ? "" : ", status " + image.getStatus()));
            }
            Files.createDirectories(svg.getParent());
            Files.write(svg, out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Rendering " + puml + " failed.", e);
        }
        changed.add(svgRelativePath);
    }

    /**
     * Deletes each {@code .puml} and {@code .svg} directly in the directory that this run neither wrote nor rendered,
     * and records it, so a view that was removed or renamed cannot leave a frozen diagram behind for a page to embed.
     */
    void prune(String directoryRelativePath) {
        var directory = root.resolve(directoryRelativePath);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var files = Files.list(directory)) {
            var stale = files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().matches(".*\\.(puml|svg)"))
                    .filter(file -> !produced.contains(file.normalize()))
                    .sorted()
                    .toList();
            for (var file : stale) {
                Files.delete(file);
                changed.add(root.relativize(file) + " (removed)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Pruning " + directory + " failed.", e);
        }
    }

    void assertNothingChanged() {
        if (!changed.isEmpty()) {
            throw new AssertionError(
                    String.join(", ", changed) + " were out of date and have been rewritten; review and commit them.");
        }
    }

    private static String withPragma(Path file, String puml) {
        var firstLineEnd = puml.indexOf('\n');
        if (firstLineEnd < 0 || !puml.startsWith("@startuml")) {
            throw new IllegalArgumentException(
                    file + ": a PlantUML source starts with an @startuml line, and this one " + "does not.");
        }
        return puml.substring(0, firstLineEnd + 1) + PRAGMA + "\n" + puml.substring(firstLineEnd + 1);
    }

    /** The text without its blank lines, which PlantUML does not keep in the source it embeds. */
    private static String normalised(String text) {
        return text.lines().filter(line -> !line.isBlank()).collect(Collectors.joining("\n"));
    }

    /**
     * The source PlantUML embedded in the SVG, decoded, or null when there is none or it cannot be decoded. Comparing
     * decoded text, not the encoded string, keeps a different zlib's compression from making every SVG stale. PlantUML
     * drops blank lines from the text it embeds, so the caller compares it with {@link #normalised} source.
     */
    private static String embeddedSource(String svg) {
        var matcher = EMBEDDED_SOURCE.matcher(svg);
        if (!matcher.find()) {
            return null;
        }
        try {
            return normalised(TranscoderUtil.getDefaultTranscoder().decode(matcher.group(1)));
        } catch (NoPlantumlCompressionException e) {
            return null;
        }
    }
}
