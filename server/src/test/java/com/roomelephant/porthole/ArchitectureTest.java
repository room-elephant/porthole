package com.roomelephant.porthole;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.roomelephant.porthole", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final String ROOT = "com.roomelephant.porthole.";

    @ArchTest
    static final ArchRule coreDoesNotDependOnAdaptersConfigurationOrTransportLibraries = noClasses()
            .that()
            .resideInAPackage(ROOT + "core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    ROOT + "inbound..",
                    ROOT + "outbound..",
                    ROOT + "config..",
                    "com.github.dockerjava..",
                    "jakarta.servlet..",
                    "java.net.http..",
                    "org.springframework.http..",
                    "org.springframework.web..",
                    "org.springframework.boot.health..");

    @ArchTest
    static final ArchRule inboundAdaptersUseCoreModelsAndInboundPorts = classes()
            .that()
            .resideInAPackage(ROOT + "inbound..")
            .should()
            .onlyDependOnClassesThat(resideOutsideOfPackage("com.roomelephant.porthole..")
                    .or(resideInAnyPackage(
                            ROOT + "inbound..",
                            ROOT + "core.model..",
                            ROOT + "core.port.inbound..",
                            ROOT + "config.properties..",
                            ROOT + "shared..",
                            // The legacy container DTO names each container's entry ID until the dashboard replaces it.
                            ROOT + "entries")));

    @ArchTest
    static final ArchRule outboundAdaptersUseCoreModelsAndOutboundPorts = classes()
            .that()
            .resideInAPackage(ROOT + "outbound..")
            .should()
            .onlyDependOnClassesThat(resideOutsideOfPackage("com.roomelephant.porthole..")
                    .or(resideInAnyPackage(
                            ROOT + "outbound..",
                            ROOT + "core.model..",
                            ROOT + "core.port.outbound..",
                            ROOT + "config.properties..",
                            ROOT + "shared..")));

    @ArchTest
    static final ArchRule fleetDependsOnNoOtherProductionPackage = classes()
            .that()
            .resideInAPackage(ROOT + "fleet..")
            .should()
            .onlyDependOnClassesThat(resideOutsideOfPackage("com.roomelephant.porthole..")
                    .or(resideInAnyPackage(ROOT + "fleet..", ROOT + "shared..")));

    @ArchTest
    static final ArchRule fleetRootDoesNotDependOnItsAdapters = noClasses()
            .that()
            .resideInAPackage(ROOT + "fleet")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ROOT + "fleet.docker..", ROOT + "fleet.web..");

    @ArchTest
    static final ArchRule fleetDockerAdapterDoesNotDependOnWeb = noClasses()
            .that()
            .resideInAPackage(ROOT + "fleet.docker..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ROOT + "fleet.web..");

    @ArchTest
    static final ArchRule fleetWebDoesNotDependOnDockerAdapter = noClasses()
            .that()
            .resideInAPackage(ROOT + "fleet.web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ROOT + "fleet.docker..");

    @ArchTest
    static final ArchRule sharedDependsOnNothingInPorthole = classes()
            .that()
            .resideInAPackage(ROOT + "shared..")
            .should()
            .onlyDependOnClassesThat(
                    resideOutsideOfPackage("com.roomelephant.porthole..").or(resideInAPackage(ROOT + "shared..")));

    @ArchTest
    static final ArchRule portsAreInterfaces =
            classes().that().resideInAPackage(ROOT + "core.port..").should().beInterfaces();

    @ArchTest
    static final ArchRule portsDependOnlyOnCoreModelsJdkAndNullabilityAnnotations = classes()
            .that()
            .resideInAPackage(ROOT + "core.port..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(ROOT + "core.port..", ROOT + "core.model..", "java..", "org.jspecify.annotations..");

    @ArchTest
    static final ArchRule entriesDependsOnlyOnFleetAndShared = classes()
            .that()
            .resideInAPackage(ROOT + "entries..")
            .should()
            .onlyDependOnClassesThat(resideOutsideOfPackage("com.roomelephant.porthole..")
                    .or(resideInAnyPackage(ROOT + "entries..", ROOT + "fleet", ROOT + "shared..")));

    @ArchTest
    static final ArchRule entriesRootDoesNotDependOnItsAdapters = noClasses()
            .that()
            .resideInAPackage(ROOT + "entries")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ROOT + "entries.store..", ROOT + "entries.icons..", ROOT + "entries.web..");

    @ArchTest
    static final ArchRule entriesStoreDoesNotDependOnOtherEntriesAdapters = noClasses()
            .that()
            .resideInAPackage(ROOT + "entries.store..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ROOT + "entries.icons..", ROOT + "entries.web..");

    @ArchTest
    static final ArchRule entriesIconsDoesNotDependOnOtherEntriesAdapters = noClasses()
            .that()
            .resideInAPackage(ROOT + "entries.icons..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ROOT + "entries.store..", ROOT + "entries.web..");

    @ArchTest
    static final ArchRule entriesWebDoesNotDependOnOtherEntriesAdapters = noClasses()
            .that()
            .resideInAPackage(ROOT + "entries.web..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ROOT + "entries.store..", ROOT + "entries.icons..");
}
