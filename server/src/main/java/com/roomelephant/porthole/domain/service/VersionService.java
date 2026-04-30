package com.roomelephant.porthole.domain.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerConfig;
import com.roomelephant.porthole.domain.component.RegistryService;
import com.roomelephant.porthole.domain.model.VersionDTO;
import com.roomelephant.porthole.domain.model.exception.DockerUnavailableException;
import com.roomelephant.porthole.domain.model.exception.NotFoundException;
import com.roomelephant.porthole.domain.model.exception.UnexpectedException;
import com.roomelephant.porthole.domain.util.ImageUtils;
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

        var config = container.getConfig();
        if (config == null || config.getImage() == null) {
            return new VersionDTO(null, null, false);
        }

        String imageFull = config.getImage();

        String currentVersion = getVersionFromContainer(config, imageFull);

        List<String> repoDigests = getRepoDigests(container.getImageId());
        boolean isLocalImage = repoDigests == null || repoDigests.isEmpty();

        if (isLocalImage) {
            return new VersionDTO(currentVersion, null, false);
        }

        String latestVersion = registryService.getLatestVersion(imageFull);
        boolean updateAvailable = checkForUpdate(imageFull, currentVersion, latestVersion, repoDigests);

        return new VersionDTO(currentVersion, latestVersion, updateAvailable);
    }

    private @Nullable List<String> getRepoDigests(@NonNull String imageId) {
        try {
            var inspectImage = dockerClient.inspectImageCmd(imageId).exec();
            return inspectImage.getRepoDigests();
        } catch (Exception e) {
            log.error("Failed to inspect image: " + imageId, e);
            return null;
        }
    }

    private String getVersionFromContainer(@NonNull ContainerConfig config, @NonNull String imageFull) {
        String imageName = ImageUtils.extractName(imageFull);

        String envVersion = getVersionFromEnvVars(config.getEnv(), imageName);
        if (envVersion != null) {
            return envVersion;
        }

        String labelVersion = getVersionFromLabels(config.getLabels());
        if (labelVersion != null) {
            return labelVersion;
        }

        return ImageUtils.extractTag(imageFull);
    }

    private @Nullable String getVersionFromEnvVars(String @Nullable [] envs, @NonNull String imageName) {
        if (envs == null) {
            return null;
        }

        String targetEnv = imageName.toUpperCase().replaceAll("[^A-Z0-9]", "_") + "_VERSION=";

        for (String env : envs) {
            if (env.startsWith(targetEnv)) {
                String[] parts = env.split("=", 2);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    return parts[1];
                }
            }
            if (env.startsWith("VERSION=")) {
                String[] parts = env.split("=", 2);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    private @Nullable String getVersionFromLabels(@Nullable Map<String, String> labels) {
        if (labels == null) {
            return null;
        }
        if (labels.containsKey(LABEL_OCI_IMAGE_VERSION)) {
            return labels.get(LABEL_OCI_IMAGE_VERSION);
        }
        if (labels.containsKey(LABEL_IMAGE_VERSION)) {
            return labels.get(LABEL_IMAGE_VERSION);
        }
        return null;
    }

    private boolean checkForUpdate(
            @NonNull String imageFull,
            @Nullable String currentVersion,
            @Nullable String latestVersion,
            @NonNull List<String> repoDigests) {
        String tag = ImageUtils.extractTag(imageFull);

        try {
            String remoteDigest = registryService.getDigest(imageFull, tag);
            if (remoteDigest != null) {
                boolean match = repoDigests.stream().anyMatch(rd -> rd.contains(remoteDigest));
                if (!match) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Error checking for update: " + imageFull, e);
        }

        if (ImageUtils.isSemver(tag) && currentVersion != null && latestVersion != null) {
            return !currentVersion.equals(latestVersion);
        }

        return false;
    }

    private boolean isDockerConnectionError(RuntimeException e) {
        return e.getCause() instanceof SocketException;
    }
}
