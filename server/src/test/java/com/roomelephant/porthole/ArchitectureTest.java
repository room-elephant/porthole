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
                            ROOT + "shared..")));

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
                            ROOT + "shared..",
                            // Legacy icon adapters use the entries image types until phase 2 deletes them.
                            ROOT + "entries")));

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
            .resideInAnyPackage(
                    ROOT + "core.port..",
                    ROOT + "core.model..",
                    ROOT + "entries",
                    "java..",
                    "org.jspecify.annotations..");
}
