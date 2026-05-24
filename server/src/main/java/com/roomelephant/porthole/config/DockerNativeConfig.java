package com.roomelephant.porthole.config;

import java.util.Arrays;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.type.filter.AssignableTypeFilter;

@Configuration
@ImportRuntimeHints(DockerNativeConfig.DockerNativeHints.class)
class DockerNativeConfig {

    static class DockerNativeHints implements RuntimeHintsRegistrar {

        private static final MemberCategory[] CATEGORIES = {
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.DECLARED_FIELDS
        };

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            registerPackage(hints, classLoader, "com.github.dockerjava.api.model");
            registerPackage(hints, classLoader, "com.github.dockerjava.api.command");
        }

        private void registerPackage(RuntimeHints hints, ClassLoader classLoader, String basePackage) {
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
            for (var bd : scanner.findCandidateComponents(basePackage)) {
                try {
                    registerClass(hints, classLoader.loadClass(bd.getBeanClassName()));
                } catch (ClassNotFoundException ignored) {}
            }
        }

        private void registerClass(RuntimeHints hints, Class<?> clazz) {
            hints.reflection().registerType(clazz, CATEGORIES);
            Arrays.stream(clazz.getDeclaredClasses()).forEach(inner -> registerClass(hints, inner));
        }
    }
}
