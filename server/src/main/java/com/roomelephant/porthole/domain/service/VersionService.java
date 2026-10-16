package com.roomelephant.porthole.domain.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import com.roomelephant.porthole.domain.component.RegistryService;
import com.roomelephant.porthole.domain.model.ImageReference;
import com.roomelephant.porthole.domain.model.VersionDTO;
import com.roomelephant.porthole.domain.model.exception.DockerUnavailableException;
import com.roomelephant.porthole.domain.model.exception.NotFoundException;
import com.roomelephant.porthole.domain.model.exception.UnexpectedException;
import com.roomelephant.porthole.domain.util.ImageUtils;
import com.roomelephant.porthole.domain.util.NumericVersion;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VersionService {

    private static final String LABEL_OCI_IMAGE_VERSION = "org.opencontainers.image.version";
    private static final String LABEL_IMAGE_VERSION = "version";

    private final DockerClient dockerClient;
    private final RegistryService registryService;

    public VersionService(DockerClient dockerClient, RegistryService registryService) {
        this.dockerClient = dockerClient;
        this.registryService = registryService;
    }

    public @NonNull VersionDTO getVersionInfo(@NonNull String containerId) {
        InspectContainerResponse container;
        try {
            container = dockerClient.inspectContainerCmd(containerId).exec();
        } catch (com.github.dockerjava.api.exception.NotFoundException _) {
            throw new NotFoundException(containerId);
        } catch (RuntimeException e) {
            if (isDockerConnectionError(e)) {
                throw new DockerUnavailableException(e);
            }
            throw new UnexpectedException(e);
        }

        ContainerConfig config = container.getConfig();
        if (config == null || config.getImage() == null) {
            return new VersionDTO(null, null, false);
        }

        String imageFull = config.getImage();

        String currentVersion = getVersionFromContainer(config, imageFull);

        var reference = ImageReference.tryParse(imageFull).filter(ImageReference::supportsUpdates);
        if (reference.isEmpty()) return new VersionDTO(currentVersion, null, false);
        List<String> repoDigests = getRepoDigests(container.getImageId());
        List<String> matchingDigests =
                repoDigests == null ? List.of() : reference.get().matchingDigests(repoDigests);
        if (matchingDigests.isEmpty()) {
            return new VersionDTO(currentVersion, null, false);
        }

        String latestVersion = registryService.getLatestVersion(imageFull);
        boolean updateAvailable = checkForUpdate(imageFull, currentVersion, latestVersion, matchingDigests);

        return new VersionDTO(currentVersion, latestVersion, updateAvailable);
    }

    private @Nullable List<String> getRepoDigests(@NonNull String imageId) {
        try {
            InspectImageResponse inspectImage =
                    dockerClient.inspectImageCmd(imageId).exec();
            return inspectImage.getRepoDigests();
        } catch (Exception e) {
            log.error("Failed to inspect image: " + imageId, e);
            return null;
        }
    }

    private @Nullable String getVersionFromContainer(@NonNull ContainerConfig config, @NonNull String imageFull) {
        String imageName = ImageUtils.extractName(imageFull);

        String envVersion = getVersionFromEnvVars(config.getEnv(), imageName);
        if (envVersion != null) {
            return envVersion;
        }

        String labelVersion = getVersionFromLabels(config.getLabels());
        if (labelVersion != null) {
            return labelVersion;
        }

        return ImageReference.tryParse(imageFull)
                .map(ref -> ref.tag() != null ? ref.tag() : ref.lookupTag())
                .orElse(null);
    }

    private @Nullable String getVersionFromEnvVars(String @Nullable [] envs, @NonNull String imageName) {
        if (envs == null) {
            return null;
        }

        String targetEnv = imageName.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "_") + "_VERSION=";
        String generic = null;
        for (String env : envs) {
            if (env.startsWith(targetEnv)) {
                String value = nonBlank(env.substring(targetEnv.length()));
                if (value != null) return value;
            }
            if (env.startsWith("VERSION=") && generic == null) generic = nonBlank(env.substring("VERSION=".length()));
        }
        return generic;
    }

    private @Nullable String nonBlank(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private @Nullable String getVersionFromLabels(@Nullable Map<String, String> labels) {
        if (labels == null) {
            return null;
        }
        String ociVersion = nonBlank(labels.get(LABEL_OCI_IMAGE_VERSION));
        return ociVersion != null ? ociVersion : nonBlank(labels.get(LABEL_IMAGE_VERSION));
    }

    private boolean checkForUpdate(
            @NonNull String imageFull,
            @Nullable String currentVersion,
            @Nullable String latestVersion,
            @NonNull List<String> repoDigests) {
        String tag = ImageUtils.extractTag(imageFull);

        try {
            if (registryService.isTagChanged(imageFull, repoDigests)) return true;
        } catch (Exception e) {
            log.error("Error checking for update: " + imageFull, e);
        }

        var current = NumericVersion.tryParse(currentVersion);
        var latest = NumericVersion.tryParse(latestVersion);
        if (ImageUtils.isSemver(tag) && current.isPresent() && latest.isPresent()) {
            return latest.get().compareTo(current.get()) > 0;
        }

        return false;
    }

    private boolean isDockerConnectionError(RuntimeException e) {
        return e.getCause() instanceof SocketException;
    }
}
