# Architecture

[← Back to Development Guide](DEVELOPMENT.md)

Porthole is designed as a monolithic, single-artifact application for simplicity of deployment.

## Tech Stack

### Backend
- **Framework**: Spring Boot
- **Language**: Java (virtual threads enabled)
- **Docker Client**: [docker-java](https://github.com/docker-java/docker-java) with `ZeroDepDockerHttpClient` (Unix Socket support)
- **Build Tool**: Maven

### Client
- **Framework**: React
- **Bundler**: Vite
- **Styling**: Vanilla CSS (Modern, Variables-based)
- **State**: React Query + LocalStorage (for user preferences)
- **Testing**: Vitest + React Testing Library

## Build Process

The client and server are built independently, then combined into a GraalVM native executable:

1. **Client Build**: `npm run build` produces a static bundle in `client/dist/`.
2. **Resource Copying**: Maven copies `client/dist` into `server/target/classes/static` via the `copy-client` profile.
3. **Native Compilation**: GraalVM compiles the combined application into a native executable.

The client and server builds are decoupled — in CI the client artifact is produced in a separate job and passed to the server build. Locally, `make bundle-native` runs both steps in sequence.

The final Docker image runs a single native binary with no JVM required.

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

Three separate workflows run on push/PR to `main`, each triggered by path filters:

- **ci-server.yml**: Builds and tests the backend — triggered by `server/**` changes. Runs integration tests against the JVM JAR (`Dockerfile.jvm`) for fast feedback on business logic and API correctness.
- **ci-client.yml**: Builds and tests the frontend — triggered by `client/**` changes.
- **ci-docker.yml**: Verifies the Docker image build — triggered by `docker/**` changes.

### Release Workflow

Runs when a version tag (e.g., `v1.0.0`) is pushed:

- **server-check / client-check / docker-check**: Same checks as CI — run in parallel as a gate before the binary build.
- **build-binary** *(matrix: amd64, arm64)*: Sets the project version, downloads the pre-built client artifact, and compiles the GraalVM native executable. Uploads the binary as an artifact.
- **native-it** *(matrix: amd64, arm64)*: Downloads the native binary, builds a production Docker image (`docker/Dockerfile`), and runs the full integration test suite against it. The registry push is gated behind this job — a failing IT blocks the release.
- **push-image** *(matrix: amd64, arm64)*: Builds and pushes architecture-specific images to GHCR.
- **manifest**: Creates a multi-arch manifest combining the two images, pushes the version tag (and `latest` for stable releases), and creates the GitHub Release with the native binaries attached.

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
| `/api/containers`                       | GET    | Returns all containers. Supports `includeWithoutEndpoints` and `includeStopped` query params    |
| `/api/containers/{containerId}/version` | GET    | Returns version info for a container (current version, latest version, update availability) |
| `/actuator/health`                      | GET    | Health check with Docker connectivity status                                                |

### Container Endpoint Contract

`GET /api/containers` returns `endpoints` and `hasEndpoints` alongside existing container metadata. Each endpoint has exactly these fields:

```json
{
  "id": "traefik:https://app.example.com/",
  "source": "traefik",
  "url": "https://app.example.com/"
}
```

Raw labels, `exposedPorts`, `hasPublicPorts`, and endpoint `port` are not API fields. `includeWithoutEndpoints=false` filters after discovery, so label-only services are included. Both query flags default to false. The former `includeWithoutPorts` parameter is not an alias.

### Endpoint Provider Extension

`ContainerMapper` builds an immutable `ContainerSnapshot` with published ports and raw labels. The controller extracts the request server name and passes that plain hostname to `ContainerService`. The service uses the domain `EndpointContextResolver` to apply the configured hostname override and build an immutable, request-specific `EndpointContext`, which it passes through the mapper into discovery. Servlet request handling remains in `controller/`; domain components receive no servlet objects and retain no request state.

`EndpointDiscovery` constructor-injects `List<EndpointProvider>`. Any additional Spring `@Component` implementing the interface participates automatically; there is no registry or provider enum. Providers return all their candidates, use namespaced stable IDs, and never select a default. Discovery deduplicates IDs and sorts by source and URL. The client groups unfamiliar source identifiers dynamically.

For example, this illustrative custom provider uses an application-specific label (not a standard proxy label):

```java
@Component
@NullMarked
public class MyProxyEndpointProvider implements EndpointProvider {
    @Override
    public List<EndpointDTO> discover(ContainerSnapshot container, EndpointContext context) {
        String host = container.labels().get("myproxy.host");
        if (host == null || !EndpointUrls.isValidHost(host)) return List.of();
        String url = EndpointUrls.routeUrl("https", host, "/");
        return List.of(new EndpointDTO("my-proxy:" + url, "my-proxy", url));
    }
}
```

Place implementations in `domain/component/endpoint/`; shared input/output records live in `domain/model/` and URL helpers in `domain/util/`. Endpoint configuration lives in `config/properties/EndpointProperties`. New reflectively accessed types require Spring AOT binding hints; generated reachability metadata must not be edited by hand.

Published-port IDs use `published-port:<port>` independently of the request hostname. Traefik IDs use `traefik:<normalized-url>`. The client opens the API URL directly, resolves saved IDs before Traefik defaults, and keeps automatic defaults out of storage. See [configuration](CONFIGURATION.md#endpoint-discovery) for supported literal rules, selection, and hostname policy.

## Directory Structure

```
.
├── client/             # React frontend (Vite + React 19)
├── server/             # Spring Boot backend (Java 25)
├── docker/             # Docker configuration (see docker/README.md)
└── docs/               # Documentation
```

## Server Package Layout

`server/src/main/java/com/roomelephant/porthole/`:

- `controller/` — REST endpoints (`ContainerController`) and `GlobalExceptionHandler`
- `domain/service/` — `ContainerService` (lists containers), `VersionService` (version + update detection)
- `domain/component/` — `IconComponent` (icon resolution), `RegistryService` (Docker Hub queries), and `endpoint/` providers/discovery
- `domain/mapper/` — `ContainerMapper` maps docker-java types to DTOs
- `domain/model/` — `ContainerDTO`, `EndpointDTO`, `ContainerSnapshot`, `EndpointContext`, `VersionDTO`, and domain exceptions
- `domain/util/` — `ImageUtils` (image name parsing, Docker Hub namespace normalization)
- `config/` — `DockerConfig` (docker-java client bean), `IconConfig` (loads icons.yml), `RestClientConfig`, `DockerHealthIndicator`
- `config/properties/` — `@ConfigurationProperties` beans: `DockerProperties`, `RegistryProperties`, `DashboardProperties`, `EndpointProperties`
- `config/nativehints/` — `DockerNativeConfig` (Spring AOT hints for types the agent can't observe)

## Integration Test Infrastructure

`server/src/test/java/.../it/`:

- `PortholeContainer` — Testcontainers wrapper that launches the application image; supports optional native-image agent attachment for hint generation
- `IntegrationTestBase` — base class wiring WireMock (Docker Hub mock) and shared Porthole container
- `ContainerAwareIntegrationTestBase` — extends base to also start real Docker containers for end-to-end scenarios
- Tests in `controller/`, `health/`, and `resilience/` extend these bases

Endpoint discovery requires Docker-backed component scenarios in `server/src/test/java/com/roomelephant/porthole/it/controller/`, exercising the packaged JVM and native applications. Unit and MockMvc tests complement these scenarios. `PortholeContainer` defaults to `porthole-jvm:latest`; `-Dporthole.it.image=<tag>` selects a different runtime for both normal and custom-socket factories. Only the shared app records agent hints, avoiding competing writers from isolated override-test instances. See [server test commands](../server/README.md#test).
