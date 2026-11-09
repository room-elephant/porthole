package com.roomelephant.porthole;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;

/**
 * The feature-slice rules: which slice may use which, and how a slice's root package relates to its adapters. Checked
 * against production bytecode only.
 */
@AnalyzeClasses(packages = "com.roomelephant.porthole", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final String BASE = "com.roomelephant.porthole";
    private static final String ROOT = BASE + ".";
    private static final List<String> SLICES = List.of("fleet", "endpoints", "versions", "entries", "dashboard");
    private static final List<String> ADAPTERS = List.of("docker", "registry", "store", "icons", "web");

    @ArchTest
    static final ArchRule slicesAreFreeOfCycles =
            slices().matching(ROOT + "(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule fleetDependsOnNoSlice = sliceOnlyUses("fleet");

    @ArchTest
    static final ArchRule endpointsDependOnlyOnFleet = sliceOnlyUses("endpoints", ROOT + "fleet");

    @ArchTest
    static final ArchRule versionsDependOnlyOnFleet = sliceOnlyUses("versions", ROOT + "fleet");

    @ArchTest
    static final ArchRule entriesDependOnlyOnFleet = sliceOnlyUses("entries", ROOT + "fleet");

    @ArchTest
    static final ArchRule dashboardUsesOtherSlicesThroughTheirRoots =
            sliceOnlyUses("dashboard", ROOT + "fleet", ROOT + "endpoints", ROOT + "versions", ROOT + "entries");

    @ArchTest
    static final ArchRule sharedDependsOnNoSlice = sliceOnlyUses("shared");

    @ArchTest
    static final ArchRule nativeHintsDependOnNoSlice = sliceOnlyUses("nativehints");

    @ArchTest
    static final ArchRule nothingDependsOnDashboard = noClasses()
            .that()
            .resideOutsideOfPackage(ROOT + "dashboard..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ROOT + "dashboard..");

    @ArchTest
    static final ArchRule sliceRootsDoNotImportTheirOwnAdapters =
            classes().that(areInASliceRoot()).should(notDependOnAdapters(true));

    @ArchTest
    static final ArchRule webPackagesDoNotImportOtherSlicesAdapters =
            classes().that().resideInAPackage(ROOT + "*.web..").should(notDependOnAdapters(false));

    @ArchTest
    static final ArchRule sliceRootsStayFreeOfTransportLibraries = noClasses()
            .that(areInASliceRoot())
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.github.dockerjava..",
                    "jakarta.servlet..",
                    "java.net.http..",
                    "org.springframework.http..",
                    "org.springframework.web..",
                    "org.springframework.boot.health..");

    /** Classes of the slice may use the JDK and libraries, their own slice, {@code shared}, and the given packages. */
    private static ArchRule sliceOnlyUses(String slice, String... allowed) {
        String[] packages = new String[allowed.length + 2];
        packages[0] = ROOT + slice + "..";
        packages[1] = ROOT + "shared..";
        System.arraycopy(allowed, 0, packages, 2, allowed.length);
        return classes()
                .that()
                .resideInAPackage(ROOT + slice + "..")
                .should()
                .onlyDependOnClassesThat(resideOutsideOfPackage(BASE + "..").or(resideInAnyPackage(packages)));
    }

    /** The slice a class belongs to, and the rest of its package below the slice. */
    private record Location(String slice, String rest) {
        static Optional<Location> of(JavaClass type) {
            String name = type.getPackageName();
            if (!name.startsWith(ROOT)) return Optional.empty();
            String[] parts = name.substring(ROOT.length()).split("\\.", 2);
            return Optional.of(new Location(parts[0], parts.length > 1 ? parts[1] : ""));
        }

        boolean isAdapter() {
            return SLICES.contains(slice) && ADAPTERS.contains(rest.split("\\.", 2)[0]);
        }
    }

    private static DescribedPredicate<JavaClass> areInASliceRoot() {
        return new DescribedPredicate<>("are in a slice's root package") {
            @Override
            public boolean test(JavaClass type) {
                return Location.of(type)
                        .filter(location -> SLICES.contains(location.slice())
                                && location.rest().isEmpty())
                        .isPresent();
            }
        };
    }

    /**
     * With {@code ownSlice}, forbids the class's own slice's adapters; otherwise forbids every other slice's adapters.
     */
    private static ArchCondition<JavaClass> notDependOnAdapters(boolean ownSlice) {
        String description =
                ownSlice ? "not depend on their own slice's adapters" : "not depend on other slices' adapters";
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                String slice = Location.of(type).orElseThrow().slice();
                for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
                    Optional<Location> target = Location.of(dependency.getTargetClass());
                    if (target.isEmpty() || !target.get().isAdapter()) continue;
                    if (target.get().slice().equals(slice) == ownSlice)
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }
}
