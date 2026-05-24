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

## Native Image Hints

GraalVM native compilation requires explicit configuration for reflection, proxies, JNI, and resource access that cannot be statically inferred. These hints live in:

```
server/src/main/resources/META-INF/native-image/com.roomelephant/porthole/reachability-metadata.json
```

### Automated generation

Hints are generated automatically by a dedicated Maven profile (`generate-native-hints`) that runs the integration test suite with the GraalVM native-image agent attached to the app container. The agent observes all runtime reflection and writes the output directly to the source-tree path via a volume mount. No manual post-processing is required.

```
mvn verify -Pgenerate-native-hints
    │
    ├── failsafe passes system property: native.agent.output.dir=<source-tree path>
    │
    ├── PortholeContainer (if property set):
    │     ├── env: JAVA_TOOL_OPTIONS=-agentlib:native-image-agent=config-output-dir=/tmp/native-hints
    │     └── volume: <source-tree path> → /tmp/native-hints (READ_WRITE)
    │
    ├── ITs run normally (all *IT.java classes)
    │
    └── on container stop → agent flushes hint files → appear on host immediately
```

The `Dockerfile.jvm` uses a GraalVM JDK 25 community image so the agent is available in the IT container. Regular IT runs (`-Pintegration-tests`) are unaffected — the agent is a no-op unless `JAVA_TOOL_OPTIONS` is set, which only happens under the generation profile.

### Spring AOT hints

`DockerNativeConfig` (`server/src/main/java/.../config/nativehints/DockerNativeConfig.java`) registers additional Spring AOT hints for `docker-java` model classes and `@ConfigurationProperties` beans that are bound reflectively by Jackson or Hibernate Validator. This covers types that the agent cannot observe because they are never instantiated during the IT run.

### JVM vs Native Integration Tests

The integration test suite runs twice in the pipeline, each time against a different runtime:

| | CI (`reusable-server.yml`) | Release (`native-it` job) |
|---|---|---|
| **Runtime** | JVM JAR (`Dockerfile.jvm`) | Native binary (`docker/Dockerfile`) |
| **Trigger** | Every push / PR to `main` | Every release tag |
| **Purpose** | Catch logic and API bugs fast | Catch AOT/native-image failures |

A feature can pass all JVM tests and still fail in native. GraalVM AOT compilation requires explicit configuration for reflection, proxies, and JNI — any class or method not registered will be missing at runtime. These failures are invisible on the JVM because the JVM resolves everything dynamically.

Running the same IT suite against the native binary surfaces missing reflection config, incomplete AOT hints, or `ClassNotFoundException`s before the image is pushed. Both runs are necessary; neither substitutes for the other.

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

## Docker Runtime Design

### Base Image Strategy
Given a standard GraalVM native-image build (glibc-based, dynamically linked), **`debian:bookworm-slim`** is the correct runtime choice instead of Alpine.
- **Reason**: The native binary built by GraalVM is dynamically linked against `glibc` (present in the build stage).
- **Compatibility**: Alpine uses `musl`, which makes it incompatible with standard glibc-linked binaries without complex static linking configurations.
- **Trade-off**: Bookworm-slim offers a stable, multi-arch foundation and is relatively small (~75MB), providing a good balance between size and compatibility.

### Permissions & Security

To run the application as a non-root user while minimising container-level privileges and enabling optional access to the host Docker daemon:

1. **Build Time**:
   - Creates a `nonroot` user with UID/GID 65532.
   - **No privileged group memberships are baked into the image.**

2. **Run Time (Entrypoint)**:
   - The container starts as `root` to perform one-time setup.
   - **Docker Socket Detection**: If `/var/run/docker.sock` is mounted, the entrypoint inspects its owning GID.
   - **Root Ownership (GID 0)**:
     - If the socket is owned by `root` (GID 0), often the case for Docker Desktop or rootless setups, the container **stays as root**.
     - This ensures seamless access without complex group mapping in these environments.
   - **Dynamic Group Reconciliation** (if GID != 0):
     - If the socket's GID does not exist in the container, a group (`dockersock-<GID>`) is created.
     - The `nonroot` user is added to this group if not already a member.
     - **Privilege Drop**: The entrypoint switches to the `nonroot` user via `gosu` before launching the application.
   - **Access Verification**: A read/write check is performed to warn about potential permission issues.

The application process itself runs without root privileges and is granted only the minimum group access required to communicate with the host's Docker daemon when the socket is mounted.

> [!NOTE]
> Access to `/var/run/docker.sock` effectively grants control over the host Docker daemon. This setup limits container privileges but does not provide isolation from the host.

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
├── client/             # React frontend (Vite + React 19)
├── server/             # Spring Boot backend (Java 25)
├── docker/             # Production Docker configuration
│   ├── Dockerfile       # CI/release image (wraps pre-built native binary)
│   ├── Dockerfile.jvm    # Integration test image (JVM JAR + native-image agent)
│   └── entrypoint.sh   # Socket group reconciliation and privilege drop
└── docs/               # Documentation
```
