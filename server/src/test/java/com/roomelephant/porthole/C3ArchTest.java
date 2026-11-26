package com.roomelephant.porthole;

import static org.assertj.core.api.Assertions.assertThat;

import com.structurizr.Workspace;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.dsl.StructurizrDslParserException;
import com.structurizr.export.Diagram;
import com.structurizr.export.plantuml.C4PlantUMLExporter;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Relationship;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The server's components in {@code docs/architecture/c4model.c3.dsl} match the code: each component names its
 * packages in {@code pkg:} tags, and its arrows must be the code's dependencies between those components, no more and
 * no fewer. {@link ArchitectureTest} says which dependencies are allowed; this test keeps the picture of the ones that
 * exist honest. It also exports every view of the workspace as C4-PlantUML and renders it.
 */
class C3ArchTest {

    private static final String BASE = "com.roomelephant.porthole";
    // Relative to the repository root, so the messages name the files as a person finds them.
    private static final String WORKSPACE = "docs/architecture/c4model.c1+c2.dsl";
    private static final String C3 = "docs/architecture/c4model.c3.dsl";
    private static final String VIEWS = "docs/architecture/generated/c4views/";
    private static final String SYSTEM = "Porthole";
    private static final String SERVER = "Application server";
    private static final String PKG = "pkg:";
    // Every slice may use shared, so arrows into it are not drawn. The exemption is one-way: an edge from shared to
    // anything still has to be drawn.
    private static final Set<String> UNDRAWN_TARGETS = Set.of("shared");
    private static final String SERVER_VIEW = "C3-Server-Components";

    private static StructurizrDslParser parser;
    private static Workspace workspace;
    private static Container server;
    // The server's components by DSL identifier, with their package patterns in the order the tags list them; one
    // that names none is everyComponentNamesItsPackages' failure, and counting its arrows as absent from the code would
    // report it twice.
    private static Map<String, Component> components;
    private static Map<String, List<String>> patternsByComponent;
    private static JavaClasses classes;
    private static Set<String> codePackages;

    @BeforeAll
    static void load() {
        parser = new StructurizrDslParser();
        try {
            parser.parse(GeneratedDiagrams.projectRoot().resolve(WORKSPACE).toFile());
        } catch (StructurizrDslParserException e) {
            throw new AssertionError(WORKSPACE + " (with " + C3 + ") does not parse: " + e.getMessage(), e);
        }
        workspace = parser.getWorkspace();
        var system = workspace.getModel().getSoftwareSystemWithName(SYSTEM);
        assertThat(system)
                .as("%s declares no software system \"%s\"", WORKSPACE, SYSTEM)
                .isNotNull();
        server = system.getContainerWithName(SERVER);
        assertThat(server)
                .as("%s declares no container \"%s\"", WORKSPACE, SERVER)
                .isNotNull();

        components = new TreeMap<>();
        patternsByComponent = new LinkedHashMap<>();
        for (var component : server.getComponents()) {
            var id = identifier(component);
            components.put(id, component);
            var patterns = component.getTagsAsSet().stream()
                    .filter(tag -> tag.startsWith(PKG))
                    .map(tag -> tag.substring(PKG.length()).trim())
                    .toList();
            if (!patterns.isEmpty()) {
                patternsByComponent.put(id, patterns);
            }
        }

        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
        codePackages = new TreeSet<>();
        for (JavaClass type : classes) {
            if (inBase(type.getPackageName())) {
                codePackages.add(type.getPackageName());
            }
        }
    }

    @Test
    void everyPackageBelongsToAComponent() {
        var orphans = new TreeSet<String>();
        for (var pkg : codePackages) {
            if (resolveComponent(pkg) == null) {
                orphans.add(pkg);
            }
        }

        assertThat(orphans)
                .as("These packages belong to no component in %s; add them to a component as a pkg: tag", C3)
                .isEmpty();
    }

    @Test
    void everyPatternMatchesAPackage() {
        var stale = new TreeSet<String>();
        patternsByComponent.forEach((component, patterns) -> {
            for (var pattern : patterns) {
                if (codePackages.stream().noneMatch(pkg -> matchedPrefix(pattern, relative(pkg)) >= 0)) {
                    stale.add(component + ": " + PKG + pattern);
                }
            }
        });

        assertThat(stale)
                .as("These pkg: tags in %s match no production package; remove or correct them", C3)
                .isEmpty();
    }

    @Test
    void everyComponentNamesItsPackages() {
        var unmapped = new TreeSet<>(components.keySet());
        unmapped.removeAll(patternsByComponent.keySet());

        assertThat(unmapped)
                .as("These components in %s have no pkg: tag naming their packages; add one per package pattern", C3)
                .isEmpty();
    }

    @Test
    void everyCodeDependencyIsDrawn() {
        var drawn = drawnEdges();
        var missing = new TreeMap<String, String>();
        codeEdges().forEach((edge, example) -> {
            var target = edge.substring(edge.indexOf(" -> ") + 4);
            if (!drawn.contains(edge) && !UNDRAWN_TARGETS.contains(target)) {
                missing.put(edge, edge + " (e.g. " + example + ")");
            }
        });

        assertThat(missing.values())
                .as(
                        "The code has these dependencies between components that %s does not draw; add each as "
                                + "`src -> dst \"description\"`, or remove the dependency if it is a leak",
                        C3)
                .isEmpty();
    }

    @Test
    void everyDrawnDependencyIsInTheCode() {
        var phantom = new TreeSet<>(drawnEdges());
        phantom.removeAll(codeEdges().keySet());

        assertThat(phantom)
                .as("%s draws these arrows between components but the code has no such dependency; remove them", C3)
                .isEmpty();
    }

    /**
     * The parser refuses an arrow whose other end is not declared; what it lets through is an arrow without a
     * description, which C4-PlantUML draws as an empty label and some PlantUML renderers crash on.
     */
    @Test
    void everyComponentRelationshipHasADescription() {
        var unlabelled = new TreeSet<String>();
        for (var relationship : workspace.getModel().getRelationships()) {
            if (relationship.getSource() instanceof Component source
                    && relationship.getDestination() instanceof Component destination
                    && (relationship.getDescription() == null
                            || relationship.getDescription().isBlank())) {
                unlabelled.add(identifier(source) + " -> " + identifier(destination));
            }
        }

        assertThat(unlabelled)
                .as("These arrows between components in %s or %s have no description; give each one", C3, WORKSPACE)
                .isEmpty();
    }

    /**
     * The other checks read the model, and a view can still hide part of it with an {@code exclude}; this one reads the
     * view the page shows, so a component or an arrow the model has cannot be left out of the picture.
     */
    @Test
    void theServerViewShowsEveryComponentAndEveryArrowBetweenThem() {
        var view = workspace.getViews().getComponentViews().stream()
                .filter(candidate -> SERVER_VIEW.equals(candidate.getKey()))
                .findFirst()
                .orElse(null);
        assertThat(view)
                .as("%s declares no component view with the key \"%s\"", WORKSPACE, SERVER_VIEW)
                .isNotNull();

        var shown = new TreeSet<String>();
        view.getElements().forEach(element -> shown.add(element.getElement().getId()));
        var hiddenComponents = new TreeSet<String>();
        components.forEach((id, component) -> {
            if (!shown.contains(component.getId())) {
                hiddenComponents.add(id);
            }
        });
        var shownArrows = new TreeSet<String>();
        view.getRelationships().forEach(arrow -> shownArrows.add(arrow.getId()));
        var hiddenArrows = new TreeSet<String>();
        for (var relationship : workspace.getModel().getRelationships()) {
            if (isServerArrow(relationship) && !shownArrows.contains(relationship.getId())) {
                hiddenArrows.add(identifier((Component) relationship.getSource()) + " -> "
                        + identifier((Component) relationship.getDestination()));
            }
        }

        assertThat(hiddenComponents)
                .as(
                        "The view \"%s\" in %s leaves out these server components; remove the `exclude` that hides each",
                        SERVER_VIEW, WORKSPACE)
                .isEmpty();
        assertThat(hiddenArrows)
                .as(
                        "The view \"%s\" in %s leaves out these arrows between server components; remove the `exclude` "
                                + "that hides each",
                        SERVER_VIEW, WORKSPACE)
                .isEmpty();
    }

    /**
     * Writes each view as C4-PlantUML and renders it, and deletes the files of a view the workspace no longer has; a
     * run that changed any file fails asking for a commit.
     */
    @Test
    void exportViews() {
        var generated = new GeneratedDiagrams(GeneratedDiagrams.projectRoot());
        for (Diagram diagram : new C4PlantUMLExporter().export(workspace)) {
            var puml = VIEWS + diagram.getKey() + ".puml";
            generated.write(puml, diagram.getDefinition());
            generated.render(puml);
        }
        generated.prune(VIEWS);
        generated.assertNothingChanged();
    }

    /** Each code dependency between two components, with the first class pair found that makes it. */
    private static Map<String, String> codeEdges() {
        var edges = new TreeMap<String, String>();
        for (JavaClass origin : classes) {
            var from = resolveComponent(origin.getPackageName());
            if (from == null) {
                continue;
            }
            for (var dependency : origin.getDirectDependenciesFromSelf()) {
                var target = dependency.getTargetClass().getBaseComponentType();
                var to = resolveComponent(target.getPackageName());
                if (to == null || to.equals(from)) {
                    continue;
                }
                edges.putIfAbsent(from + " -> " + to, shortName(origin) + " -> " + shortName(target));
            }
        }
        return edges;
    }

    /** The drawn arrows whose two ends are both server components that name their packages. */
    private static Set<String> drawnEdges() {
        var edges = new TreeSet<String>();
        for (var relationship : workspace.getModel().getRelationships()) {
            if (isServerArrow(relationship)) {
                edges.add(identifier((Component) relationship.getSource()) + " -> "
                        + identifier((Component) relationship.getDestination()));
            }
        }
        return edges;
    }

    /** Whether the relationship joins two server components that name their packages: the set the edge checks use. */
    private static boolean isServerArrow(Relationship relationship) {
        return relationship.getSource() instanceof Component source
                && relationship.getDestination() instanceof Component destination
                && patternsByComponent.containsKey(identifier(source))
                && patternsByComponent.containsKey(identifier(destination))
                && source.getContainer() == server
                && destination.getContainer() == server;
    }

    /** The element's DSL identifier, so messages name a component as the file spells it. */
    private static String identifier(Component component) {
        var id = parser.getIdentifiersRegister().findIdentifier(component);
        return id == null ? component.getName() : id;
    }

    private static String resolveComponent(String pkg) {
        return inBase(pkg) ? resolveComponent(patternsByComponent, relative(pkg)) : null;
    }

    /**
     * The component whose matching pattern is longest, for a package relative to the base; null when none matches.
     */
    static String resolveComponent(Map<String, List<String>> patterns, String relative) {
        String best = null;
        int bestLength = -1;
        for (var entry : patterns.entrySet()) {
            for (var pattern : entry.getValue()) {
                int length = matchedPrefix(pattern, relative);
                if (length > bestLength) {
                    best = entry.getKey();
                    bestLength = length;
                }
            }
        }
        return best;
    }

    /** The length of the pattern's prefix when it matches the package, else -1. */
    static int matchedPrefix(String pattern, String relative) {
        if (pattern.endsWith(".**")) {
            var prefix = pattern.substring(0, pattern.length() - 3);
            return relative.equals(prefix) || relative.startsWith(prefix + ".") ? prefix.length() : -1;
        }
        return relative.equals(pattern) ? pattern.length() : -1;
    }

    private static boolean inBase(String pkg) {
        return pkg.equals(BASE) || pkg.startsWith(BASE + ".");
    }

    private static String relative(String pkg) {
        return pkg.equals(BASE) ? "" : pkg.substring(BASE.length() + 1);
    }

    private static String shortName(JavaClass type) {
        return type.getName().substring(BASE.length() + 1);
    }
}
