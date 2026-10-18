package com.roomelephant.porthole;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private static final Path ROOT = Path.of("src/main/java/com/roomelephant/porthole");

    @Test
    void coreDoesNotDependOnAdaptersConfigurationOrTransportLibraries() throws Exception {
        var forbidden = Pattern.compile(
                "com\\.roomelephant\\.porthole\\.(?:inbound|outbound|config|domain|controller)\\.|com\\.github\\.dockerjava\\.|jakarta\\.servlet\\.|java\\.net\\.http\\.|org\\.springframework\\.(?:http|web)\\.");
        try (var files = Files.walk(ROOT.resolve("core"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList())
                assertThat(forbidden.matcher(Files.readString(file)).find())
                        .as(file.toString())
                        .isFalse();
        }
    }

    @Test
    void controllersUseCoreInboundContracts() throws Exception {
        for (String type : List.of("ContainerController", "VersionController")) {
            Class<?> controller = Class.forName("com.roomelephant.porthole.inbound.web." + type);
            assertThat(Arrays.stream(controller.getDeclaredConstructors())
                            .flatMap(c -> Arrays.stream(c.getParameterTypes()))
                            .filter(c -> c.getName().startsWith("com.roomelephant.porthole.core.")))
                    .allMatch(c -> c.isInterface()
                            && c.getPackageName().equals("com.roomelephant.porthole.core.port.inbound"));
        }
    }

    @Test
    void portSignaturesUseOnlyCoreAndJdkTypes() throws Exception {
        Path ports = ROOT.resolve("core/port");
        try (var files = Files.walk(ports)) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = "com.roomelephant.porthole."
                        + ROOT.relativize(file).toString().replace('/', '.').replace(".java", "");
                Class<?> port = Class.forName(name);
                assertThat(port.isInterface()).isTrue();
                for (var method : port.getDeclaredMethods()) {
                    List<java.lang.reflect.Type> types = new ArrayList<>(List.of(method.getGenericParameterTypes()));
                    types.add(method.getGenericReturnType());
                    for (var type : types) {
                        var names = Pattern.compile("[a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)+")
                                .matcher(type.getTypeName());
                        while (names.find())
                            assertThat(names.group())
                                    .satisfiesAnyOf(
                                            s -> assertThat(s).startsWith("java."),
                                            s -> assertThat(s).startsWith("com.roomelephant.porthole.core."));
                    }
                }
            }
        }
    }
}
