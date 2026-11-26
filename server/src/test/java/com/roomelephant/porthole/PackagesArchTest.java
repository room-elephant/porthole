package com.roomelephant.porthole;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.plantuml.rules.PlantUmlArchCondition.Configuration.consideringOnlyDependenciesInAnyPackage;
import static com.tngtech.archunit.library.plantuml.rules.PlantUmlArchCondition.adhereToPlantUmlDiagram;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The package diagrams match the code: {@code packages.puml} draws every production package, grouped by slice, and one
 * diagram per slice that has adapters (and one for {@code versions.registry}) draws the packages inside it. For each, ArchUnit fails a dependency the diagram does not draw, the boxes must be the slice's packages, and
 * every arrow drawn must be a dependency the code has. Each diagram is rendered to {@code docs/architecture/generated/}.
 * Dependencies between slices are {@link C3ArchTest}'s.
 */
class PackagesArchTest {

    private static final String BASE = "com.roomelephant.porthole";
    private static final String SOURCES = "docs/architecture/";
    private static final String RENDERED = "docs/architecture/generated/";
    // ArchUnit's own component line: a [label] and its <<stereotype>>, the package pattern it reads.
    private static final Pattern BOX = Pattern.compile("^\\s*\\[([^\\[\\]]+)]\\s*<<([^<>]+)>>");
    // A stereotype, with the trailing `..` that takes in subpackages.
    private static final Pattern STEREOTYPE = Pattern.compile("<<([^<>]+)>>");
    // Arrows exactly as ArchUnit 1.5.1's PlantUmlPatterns reads them: whitespace, then dashes with an optional
    // [colour],
    // [hidden] or direction, and one head. A two-headed arrow (<-->, <-[#C62828]->) matches neither, so ArchUnit skips
    // it, and so does this test: a cycle is backed by two -[hidden]-> arrows.
    private static final String CENTER = "(?:left|right|up|down|\\[[^]]+])?";
    private static final Pattern RIGHT_ARROW =
            Pattern.compile("^\\s*\\[([^\\[\\]]+)]\\s+-+" + CENTER + "-*>\\s+\\[([^\\[\\]]+)]");
    private static final Pattern LEFT_ARROW =
            Pattern.compile("^\\s*\\[([^\\[\\]]+)]\\s+<-*" + CENTER + "-+\\s+\\[([^\\[\\]]+)]");

    enum Diagram {
        ALL("packages", ""),
        FLEET("fleet-packages", "fleet"),
        VERSIONS("versions-packages", "versions"),
        REGISTRY("versions-registry-packages", "versions.registry"),
        ENTRIES("entries-packages", "entries"),
        DASHBOARD("dashboard-packages", "dashboard");

        final String name;
        final String layer;

        Diagram(String name, String layer) {
            this.name = name;
            this.layer = layer;
        }

        String source() {
            return SOURCES + name + ".puml";
        }

        /** The whole code, one box per package, rather than one slice's direct subpackages. */
        boolean whole() {
            return layer.isEmpty();
        }

        String layerPackage() {
            return whole() ? BASE : BASE + "." + layer;
        }

        String layerPattern() {
            return whole() ? "..porthole.." : "..porthole." + layer + "..";
        }
    }

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    /**
     * ArchUnit's rule, over the layer's classes only: a class outside every box (another slice calling this one) would
     * otherwise be reported as contained in no component. Dependencies on {@code shared} are not drawn, since every
     * slice may use it.
     */
    @ParameterizedTest
    @EnumSource(Diagram.class)
    void adheresToDiagram(Diagram diagram) {
        classes()
                .that()
                .resideInAPackage(diagram.layerPattern())
                .should(adhereToPlantUmlDiagram(
                                GeneratedDiagrams.projectRoot().resolve(diagram.source()),
                                consideringOnlyDependenciesInAnyPackage(diagram.layerPattern()))
                        .ignoreDependenciesWithTarget(resideInAPackage("..porthole.shared..")))
                .check(classes);
    }

    @ParameterizedTest
    @EnumSource(Diagram.class)
    void diagramBoxesAreTheLayersPackages(Diagram diagram) {
        var drawn = new TreeSet<String>();
        for (var line : lines(diagram)) {
            var matcher = STEREOTYPE.matcher(line);
            while (matcher.find()) {
                drawn.add(matcher.group(1));
            }
        }

        var layer = diagram.layerPackage();
        var expected = new TreeSet<String>();
        for (JavaClass type : classes) {
            var pkg = type.getPackageName();
            if (diagram.whole()) {
                expected.add(pkg.equals(BASE) ? "..porthole" : "..porthole" + pkg.substring(BASE.length()));
            } else if (pkg.equals(layer)) {
                expected.add("..porthole." + diagram.layer);
            } else if (pkg.startsWith(layer + ".")) {
                var child = pkg.substring(layer.length() + 1).split("\\.")[0];
                expected.add("..porthole." + diagram.layer + "." + child + "..");
            }
        }

        if (diagram.whole()) {
            assertThat(drawn)
                    .as(
                            "The stereotypes in %s must be every production package, one <<..porthole.<package>>> each",
                            diagram.source())
                    .isEqualTo(expected);
            return;
        }
        assertThat(drawn)
                .as(
                        "The stereotypes in %s must be the packages of %s: one <<..porthole.%s.<name>..>> per direct "
                                + "subpackage, and <<..porthole.%s>> for the root when it holds classes",
                        diagram.source(), diagram.layer, diagram.layer, diagram.layer)
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(Diagram.class)
    void everyArrowIsInTheCode(Diagram diagram) {
        var stereotypeByLabel = new LinkedHashMap<String, String>();
        var arrows = new TreeSet<String>();
        for (var line : lines(diagram)) {
            var box = BOX.matcher(line);
            if (box.find()) {
                stereotypeByLabel.put(box.group(1).trim(), box.group(2).trim());
                continue;
            }
            var right = RIGHT_ARROW.matcher(line);
            if (right.find()) {
                arrows.add(right.group(1).trim() + " -> " + right.group(2).trim());
            }
            var left = LEFT_ARROW.matcher(line);
            if (left.find()) {
                arrows.add(left.group(2).trim() + " -> " + left.group(1).trim());
            }
        }

        var phantom = new TreeSet<String>();
        for (var arrow : arrows) {
            var ends = arrow.split(" -> ");
            var from = stereotypeByLabel.get(ends[0]);
            var to = stereotypeByLabel.get(ends[1]);
            if (from == null || to == null || !codeDepends(from, to)) {
                phantom.add("[" + ends[0] + "] --> [" + ends[1] + "]");
            }
        }

        assertThat(phantom)
                .as(
                        "%s draws these arrows, but no class in the first box depends on one in the second; remove them",
                        diagram.source())
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Diagram.class)
    void rendersTheDiagram(Diagram diagram) {
        var generated = new GeneratedDiagrams(GeneratedDiagrams.projectRoot());
        generated.render(diagram.source(), RENDERED + diagram.name + ".svg");
        generated.assertNothingChanged();
    }

    /** Whether a class in a package the first pattern matches depends on one in a package the second matches. */
    private static boolean codeDepends(String fromPattern, String toPattern) {
        var from = PackageMatcher.of(fromPattern);
        var to = PackageMatcher.of(toPattern);
        for (JavaClass origin : classes) {
            if (!from.matches(origin.getPackageName())) {
                continue;
            }
            for (var dependency : origin.getDirectDependenciesFromSelf()) {
                var target = dependency.getTargetClass().getBaseComponentType();
                if (target.getPackageName().startsWith(BASE) && to.matches(target.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The file's lines without PlantUML comments, which ArchUnit skips too. */
    private static List<String> lines(Diagram diagram) {
        var file = GeneratedDiagrams.projectRoot().resolve(diagram.source());
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.stripLeading().startsWith("'"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + file + " failed.", e);
        }
    }
}
