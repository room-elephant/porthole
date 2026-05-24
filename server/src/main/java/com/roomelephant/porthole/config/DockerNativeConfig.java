package com.roomelephant.porthole.config;

import java.util.Arrays;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.type.filter.AssignableTypeFilter;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(DockerNativeConfig.DockerNativeHints.class)
class DockerNativeConfig {

    static class DockerNativeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
            var loader = classLoader != null ? classLoader : getClass().getClassLoader();
            registerPackage(hints, loader, "com.github.dockerjava.api.model");
            registerPackage(hints, loader, "com.github.dockerjava.api.command");
            registerCoreClasses(hints, loader);
        }

        private void registerCoreClasses(RuntimeHints hints, ClassLoader classLoader) {
            for (var name : new String[] {
                "com.github.dockerjava.core.DockerConfigFile", "com.github.dockerjava.core.DockerContextMetaFile"
            }) {
                try {
                    registerClass(hints, classLoader.loadClass(name));
                } catch (ClassNotFoundException _) {
                    // class not present at build time — skip
                }
            }
        }

        private void registerPackage(RuntimeHints hints, ClassLoader classLoader, String basePackage) {
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
            for (var bd : scanner.findCandidateComponents(basePackage)) {
                try {
                    registerClass(hints, classLoader.loadClass(bd.getBeanClassName()));
                } catch (ClassNotFoundException _) {
                    // class not present at build time — skip
                }
            }
        }

        private void registerClass(RuntimeHints hints, Class<?> clazz) {
            hints.reflection()
                    .registerType(
                            clazz, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
            Arrays.stream(clazz.getDeclaredFields()).forEach(hints.reflection()::registerField);
            Arrays.stream(clazz.getDeclaredClasses()).forEach(inner -> registerClass(hints, inner));
        }
    }
}
