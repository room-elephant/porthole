# Architecture

[← Back to Development Guide](DEVELOPMENT.md)

Porthole is designed as a monolithic, single-artifact application for simplicity of deployment.

## Tech Stack

### Backend
- **Framework**: Spring Boot 4.0.0
- **Language**: Java 25
- **Concurrency**: Virtual threads enabled
- **Docker Client**: [docker-java](https://github.com/docker-java/docker-java) with `ZeroDepDockerHttpClient` (Unix Socket support).
- **Build Tool**: Maven

### Client
- **Framework**: React 19
- **Bundler**: Vite
- **Styling**: Vanilla CSS (Modern, Variables-based)
- **State**: React Query + LocalStorage (for user preferences)
- **Testing**: Vitest + React Testing Library

## Build Process

We use a "Client-First" build strategy integrated into Maven, producing a GraalVM native executable:

1.  **Client Build**: The `frontend-maven-plugin` runs `npm install` and `npm run build` in the `client/` directory.
2.  **Resource Copying**: The `maven-resources-plugin` copies the contents of `client/dist` into `server/target/classes/static`.
3.  **Native Compilation**: GraalVM compiles the application into a native executable using the `native-maven-plugin`.

This allows the final Docker image to run a single native binary without needing a JVM, resulting in faster startup (~50-100ms) and lower memory usage.

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

## Version Detection

Porthole attempts to detect the current version of each container using multiple strategies (in priority order):

1. **Image-specific environment variable**: Looks for `<IMAGE_NAME>_VERSION` (e.g., `MONGO_VERSION`, `REDIS_VERSION`)
2. **Generic environment variable**: Falls back to `VERSION` if no image-specific var exists
3. **OCI labels**: Checks `org.opencontainers.image.version` or `version` labels
4. **Image tag**: Uses the tag from the image name (e.g., `7.0` from `mongo:7.0`)

The image-specific check (step 1) takes priority because containers often have multiple `*_VERSION` env vars (like `GOSU_VERSION`, `PYTHON_VERSION`) that aren't the application version.

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

## API Endpoints

| Endpoint                                | Method | Description                                                                                 |
|-----------------------------------------|--------|---------------------------------------------------------------------------------------------|
| `/api/containers`                       | GET    | Returns all containers. Supports `includeWithoutPorts` and `includeStopped` query params    |
| `/api/containers/{containerId}/version` | GET    | Returns version info for a container (current version, latest version, update availability) |
| `/actuator/health`                      | GET    | Health check with Docker connectivity status                                                |

## Health Check

The health endpoint includes a Docker connectivity check that verifies the Docker daemon is reachable. If the Docker socket is unavailable or unresponsive, the health status will report as DOWN.

The Docker container includes a built-in HEALTHCHECK that polls this endpoint every 30 seconds.

## Response Compression

JSON responses larger than 1KB are automatically compressed using gzip.

## Graceful Shutdown

When stopping Porthole, active requests are allowed up to 20 seconds to complete before the application terminates.

## Directory Structure

```
.
├── client/             # React Application
├── server/             # Spring Boot Application
│   └── src/main/java/com/roomelephant/porthole
├── docker/             # Production Docker configuration
├── dev/                # Development Docker configuration
└── docs/               # Documentation
```
