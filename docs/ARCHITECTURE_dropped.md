# Dropped from ARCHITECTURE.md — review before discarding

## Container Status

Each container displays a status indicator (semaphore) in the top-right corner:

- **Green**: Container is running
- **Yellow**: Container is paused or restarting
- **Red**: Container is stopped, exited, or dead

Hovering over the indicator shows the full status (e.g., "Up 2 hours", "Exited (0) 3 days ago").

The UI provides toggles to:
- **Show stopped containers**: Include non-running containers (equivalent to `docker ps -a`)
- **Show containers without ports**: Include containers without exposed ports

> [!NOTE]
> **Limitation**: When showing stopped containers, exposed ports are not currently visible because they are not mapped to public ports on the host while stopped.
>
> **Future Work**: We plan to improve this by inspecting the container's configuration to detect intended exposed ports even when the container is not running.

---

## Version Detection

Porthole attempts to detect the current version of each container using multiple strategies (in priority order):

1. **Image-specific environment variable**: Looks for `<IMAGE_NAME>_VERSION` (e.g., `MONGO_VERSION`, `REDIS_VERSION`)
2. **Generic environment variable**: Falls back to `VERSION` if no image-specific var exists
3. **OCI labels**: Checks `org.opencontainers.image.version` or `version` labels
4. **Image tag**: Uses the tag from the image name (e.g., `7.0` from `mongo:7.0`)

The image-specific check (step 1) takes priority because containers often have multiple `*_VERSION` env vars (like `GOSU_VERSION`, `PYTHON_VERSION`) that aren't the application version.

---

## Docker Hub Integration

Porthole queries Docker Hub to detect available updates. When resolving image names:

- **Official images** (e.g., `redis`, `postgres`) are stored under the `library/` namespace
- **User/org images** (e.g., `bitnami/redis`) use their namespace directly

```
redis           → library/redis      (official)
bitnami/redis   → bitnami/redis      (third-party)
mongo:7         → library/mongo      (tag stripped for API calls)
```

This is required because the Docker Registry API expects the full path:
- ✅ `https://registry-1.docker.io/v2/library/redis/manifests/latest`
- ❌ `https://registry-1.docker.io/v2/redis/manifests/latest`

---

## Health Check

The health endpoint includes a Docker connectivity check that verifies the Docker daemon is reachable. If the Docker socket is unavailable or unresponsive, the health status will report as DOWN.

The Docker container includes a built-in HEALTHCHECK that polls this endpoint every 30 seconds.
