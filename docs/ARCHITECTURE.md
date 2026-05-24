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

## CI/CD Pipelines

### CI Workflow

Runs on push/PR to `main`. Detects which parts of the codebase changed and only runs relevant jobs:

- **Server job**: Builds and tests the Spring Boot backend (skipped if no `server/` changes)
- **Client job**: Builds and tests the React frontend (skipped if no `client/` changes)
- **Docker job**: Verifies the Docker image build (skipped if no `docker/` changes)

The server job runs integration tests against the **JVM-based JAR** (`Dockerfile.jvm`). This gives fast feedback on business logic and API correctness during development.

See `.github/workflows/ci.yml` for the full workflow definition.

### Release Workflow

Runs when a version tag (e.g., `v1.0.0`) is pushed:

- **Client job**: Installs, tests, and builds the React frontend, saving build artifacts.
- **Server Test job**: Runs backend tests with GraalVM.
- **Build Binary job**: Runs a matrix build for `amd64` and `arm64` that builds the GraalVM native executable and uploads it as an artifact.
- **Native Integration Tests job**: Downloads the native binary, builds a production Docker image from it (`docker/Dockerfile`), and runs the full integration test suite against the **native binary**. The registry push is gated behind this job — a failing IT blocks the release.
- **Push Image job**: Builds and pushes architecture-specific Docker images (runs only after native ITs pass).
- **Manifest job**: Creates a multi-arch Docker manifest, pushes the final version tag (and `latest` for stable releases), and creates the GitHub Release with auto-generated notes.

The Docker image is published to GitHub Container Registry at `ghcr.io/room-elephant/porthole`.

See `.github/workflows/release.yml` for the full workflow definition.

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

## API Endpoints

| Endpoint                                | Method | Description                                                                                 |
|-----------------------------------------|--------|---------------------------------------------------------------------------------------------|
| `/api/containers`                       | GET    | Returns all containers. Supports `includeWithoutPorts` and `includeStopped` query params    |
| `/api/containers/{containerId}/version` | GET    | Returns version info for a container (current version, latest version, update availability) |
| `/actuator/health`                      | GET    | Health check with Docker connectivity status                                                |

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
