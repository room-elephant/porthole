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
                            ROOT + "config.properties..")));

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
                            ROOT + "config.properties..")));

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
}
