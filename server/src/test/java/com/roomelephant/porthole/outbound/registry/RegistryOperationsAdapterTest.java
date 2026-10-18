package com.roomelephant.porthole.outbound.registry;

import static org.assertj.core.api.Assertions.*;

import com.roomelephant.porthole.config.properties.RegistryProperties;
import com.roomelephant.porthole.core.model.ImageReference;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistryOperationsAdapterTest {
    private static final RegistryProperties.Cache CACHE = new RegistryProperties.Cache(Duration.ofMinutes(5), 100);

    @Test
    void selectsTheMatchingProviderAndKeepsTheirCataloguesSeparate() {
        var hub = new Provider("docker.io", "2.0", false);
        var ghcr = new Provider("ghcr.io", "3.0", true);
        var adapter = new RegistryOperationsAdapter(List.of(ghcr, hub), CACHE);
        assertThat(adapter.getLatestVersion("acme/app:1.0")).isEqualTo("2.0");
        assertThat(adapter.getLatestVersion("ghcr.io/acme/app:1.0")).isEqualTo("3.0");
        assertThat(adapter.isTagChanged("acme/app:1.0", List.of("digest"))).isFalse();
        assertThat(adapter.isTagChanged("ghcr.io/acme/app:1.0", List.of("digest")))
                .isTrue();
    }

    @Test
    void aliasesShareACacheEntryAndInvalidationRefreshesIt() {
        var hub = new Provider("docker.io", "2.0", false);
        var adapter = new RegistryOperationsAdapter(List.of(hub), CACHE);
        assertThat(adapter.getLatestVersion("nginx:1.0")).isEqualTo("2.0");
        hub.version = "3.0";
        assertThat(adapter.getLatestVersion("index.docker.io/library/nginx:latest"))
                .isEqualTo("2.0");
        adapter.invalidateVersionCache();
        assertThat(adapter.getLatestVersion("docker.io/library/nginx:1.0")).isEqualTo("3.0");
    }

    @Test
    void missingProviderSuppliesNoUpdateEvidence() {
        var adapter = new RegistryOperationsAdapter(List.of(new Provider("docker.io", "2.0", true)), CACHE);
        assertThat(adapter.getLatestVersion("ghcr.io/acme/app:1.0")).isNull();
        assertThat(adapter.isTagChanged("ghcr.io/acme/app:1.0", List.of("digest")))
                .isFalse();
    }

    @Test
    void missingLocalDigestsCannotProveAChange() {
        var adapter = new RegistryOperationsAdapter(List.of(new Provider("docker.io", "2.0", true)), CACHE);
        assertThat(adapter.isTagChanged("nginx:1.0", List.of())).isFalse();
    }

    private static final class Provider implements RegistryClient {
        private final String registry;
        private String version;
        private final boolean changed;

        private Provider(String registry, String version, boolean changed) {
            this.registry = registry;
            this.version = version;
            this.changed = changed;
        }

        @Override
        public boolean supports(ImageReference image) {
            return registry.equals(image.registry());
        }

        @Override
        public String getLatestVersion(ImageReference image) {
            return version;
        }

        @Override
        public boolean isTagChanged(ImageReference image, List<String> digests) {
            return changed;
        }
    }
}
