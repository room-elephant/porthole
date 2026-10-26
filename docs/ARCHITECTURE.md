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
server/src/main/resources/META-INF/native-image/com.roomelephant/porthole-agent/reachability-metadata.json
```

The agent metadata uses the `porthole-agent` directory so it remains separate from
Spring AOT metadata generated under `com.roomelephant/porthole`. Both directories
are packaged on the native-image classpath; AOT generation must preserve the agent
recordings rather than overwrite them.

### Automated generation

The dedicated `generate-native-hints` Maven profile runs every component test against the GraalVM JVM application. Every application instance records: the shared app, configuration-specific apps, and custom-socket resilience apps. Each receives a fresh output directory under `server/target/native-hints/`, so concurrent agents never share files or locks.

```bash
make server-native-hints
```

The profile prepares an empty recording directory before the tests. `PortholeContainer` attaches the native-image agent whenever the profile supplies `native.agent.output.dir`. Recording instances stop gracefully so their agents flush metadata; a JUnit launcher-session listener stops the shared recording instance before JVM shutdown and Testcontainers cleanup.

After Failsafe verifies that the tests passed, `NativeHintsMerger` checks every instance produced metadata and released its lock. It runs the official `native-image-configure generate` tool from `ghcr.io/graalvm/native-image-community:25` with every recording as an input. The merged metadata is staged separately and replaces the source-tree `reachability-metadata.json` only after successful merging. Failed tests, missing recordings, active locks, or merge failures leave the published metadata unchanged. Locks are never deleted to bypass an active agent.

Regular JVM and native IT runs do not supply the recording property and therefore do not attach an agent. The JVM test image provides the agent; the separate GraalVM tool image provides merging. Hint generation requires Java 25, Maven, and Docker, without requiring a host GraalVM installation. The raw recordings and merged intermediate outputs remain under `server/target/` for inspection.

### Spring AOT hints

`DockerNativeConfig` (`server/src/main/java/.../config/nativehints/DockerNativeConfig.java`) registers additional Spring AOT hints for Docker SDK model, command, and configuration-file types used reflectively by Jackson. This covers types that the agent cannot observe because they are never instantiated during the IT run.

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
The complete environment is scanned before choosing the generic fallback. Blank values are skipped and surrounding whitespace is stripped at every metadata level.

## Container Registry Integration

Porthole queries Docker Hub and public GitHub Container Registry packages for remote image facts. Core owns the `RegistryOperations` port, numeric release selection, manifest comparison, and update decisions. `RegistryOperationsAdapter` selects a registered `RegistryClient` and caches complete raw tag catalogues, including empty ones. Providers in `dockerhub/` and `ghcr/` own their endpoints and catalogue integration; URL overrides never change the semantic image registry identity.

- **Image identity and eligibility** use parsed references and matching local RepoDigests from the owning Docker source. Docker Hub aliases normalize to `docker.io`, and official images gain `library/`. GHCR retains its complete owner/image path. Digest pins, local identifiers, unsupported registries, and images without repository provenance supply no update evidence.
- **Provider composition** uses concrete `DistributionClient` and `BearerTokenClient` collaborators. Each provider owns separate repository-scoped token state while sharing expiry, anonymous token decoding, and one refresh/retry after an authenticated request returns 401. Docker Hub catalogue requests remain unauthenticated. Private-package login and Docker credential discovery are outside this feature.
- **Catalogue pagination** parses Hub `results[].name` and JSON `next`, or Distribution `tags[]` and `Link` headers. `TagPagination` follows complete same-origin/path catalogues with cycle, page-count, and elapsed-budget checks. It returns raw tags without selecting versions. `LinkHeaderParser` handles Distribution pagination metadata.
- **Numeric releases** are selected by `ReleasePolicy` in `core.enrichment.version.resolution`, using arbitrary-precision numeric components, optional lowercase `v`, zero-padding, and deterministic alias ties. The existing dotted-numeric grammar and all-major-version policy are documented in [Registry Configuration](CONFIGURATION.md#registry-configuration).
- **Manifest facts** come from authenticated HEAD requests negotiating OCI image/index and Docker v2 image/list media types. `DistributionClient` returns validated digest and manifest-kind metadata. Core treats exact equality as unchanged and different same-algorithm digests with remotely confirmed matching manifest levels as changed. Missing old manifests, unknown types, and incompatible levels remain inconclusive. This compares repository manifests; a changed multi-platform index does not necessarily mean the installed platform changed. Platform-aware resolution is deferred.
- **Independent evidence** distinguishes available, missing, unsupported, and unavailable remote lookups. A failed catalogue lookup does not prevent manifest checking, and a failed manifest lookup does not erase a newer release. Either positive channel establishes an update; incomplete required evidence is internally inconclusive. The public `VersionInfo` JSON shape remains unchanged.
- **Refresh retention** preserves each channel's last successful facts independently for the same configured reference and installed image ID. Successful negative evidence can clear a prior positive in its channel. Changing the installed identity or losing repository provenance discards the previous evidence; Docker inspection failures retain the previous public result.

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
| `/api/containers`                       | GET    | Returns source outcomes and containers from every configured connection. Supports `includeWithoutEndpoints` and `includeStopped` query params    |
| `/actuator/health`                      | GET    | Health check with Docker connectivity status                                                |

### Container Endpoint Contract

`GET /api/containers` queries all configured Docker connections concurrently and
returns a `sources` array and a flat `containers` array:

```json
{
  "sources": [
    { "id": "walker", "name": "Walker", "status": "UP", "error": null },
    { "id": "mike", "name": "Mike", "status": "DOWN", "error": "Docker is not reachable" }
  ],
  "containers": [
    { "id": "docker-id", "sourceId": "walker", "name": "app", "displayName": "app", "image": "nginx:latest", "endpoints": [], "hasEndpoints": false, "iconUrl": "docker.webp", "project": null, "state": "running", "status": "Up", "version": null }
  ]
}
```

The existing `includeWithoutEndpoints` and `includeStopped` flags apply to all
sources. There is no per-source container endpoint or source-selection parameter.
Source order follows configuration. Each container has a required `sourceId` in
both its domain model and API DTO; Docker IDs remain unchanged. Host sections and
Compose groups use source identity before project identity.

A source with a successful list is `UP`, including an empty list. Transport
failures/deadlines are `DOWN`; other per-source processing errors are `ERROR`.
Failed sources contribute no containers. Completed inventories return 200 even
when all sources failed; global request/orchestration errors remain 5xx. Diagnostic
causes stay in server logs rather than public source errors.

Each Docker retrieval is bounded by its connection timeout plus response timeout.
`DockerDiscoveryService` retrieves sources concurrently, so the Docker retrieval
phase is bounded by the largest budget rather than the sum. Presentation and
enrichment run afterward and are outside the Docker deadline. The browser cancels
through its request signal and overrides its normal ten-second Axios timeout for
this aggregate request. A slow Docker source can delay discovery until its deadline.
The frontend polls one aggregate request every 30 seconds, replaces failed-source containers with an error state,
and does not use actuator health to suppress otherwise usable results.

`DockerSourceRegistry` owns each client's lifecycle. Mapping and enrichment receive
a source-specific `EnrichmentContext` containing the configured published
host, source ID, and the existing flags. Display-name collision detection operates
on each source's complete inventory before filtering. The frontend namespaces
React keys and stored endpoint/update preferences by source.

Each container retains `endpoints`, `hasEndpoints`, and nullable `version` with
`currentVersion`, `latestVersion`, and `updateAvailable`. `VersionRefreshService`
starts a scan per source asynchronously on readiness and repeats after the source's
completed scan plus `porthole.versions.refresh-interval` (default `1h`). Four shared
workers inspect containers/images through their owning Docker client. Results,
retry state, in-flight work, and revision guards use `(sourceId, containerId)`.
Only a successful source scan prunes that source's missing containers; one source
failure cannot erase another's results. Successful results remain cached while
refreshing; eligible misses queue deduplicated background work. HTTP enrichment
never waits for registry HTTP or version inspection. Shared complete registry catalogue
caches remain keyed by normalized registry/repository; installed version/digest
comparisons and final update decisions remain specific to the container/source.

`POST /api/versions/refresh` returns `202 Accepted` and queues immediate version
checks across all configured Docker sources. It bypasses the refresh interval and
invalidates the shared registry version catalogue cache in the background; pending
manual scans and active container checks are reused. Existing results remain visible
while checks run, and updated results are available through `GET /api/containers`. The **Version updates** section in
Porthole settings provides a **Check versions now** button with request progress,
confirmation, and retryable error feedback. The dashboard's regular polling picks
up completed checks.

`/actuator/health/docker` probes sources concurrently with a one-second common
deadline and exposes per-source status in `details.sources`. At most one ping per
source is in flight, including pings that exceed a health request's deadline.
Top-level health is UP when any source answers and DOWN when none do; failed or
unfinished sources are DOWN in the details. Managed executors stop on shutdown.

Each endpoint has exactly these fields:

```json
{
  "id": "traefik:https://app.example.com/",
  "source": "traefik",
  "url": "https://app.example.com/"
}
```

Raw labels, `exposedPorts`, `hasPublicPorts`, and endpoint `port` are not API fields. `includeWithoutEndpoints=false` filters after discovery, so label-only services are included. Both query flags default to false.

For names matching `<project>-<name>`, `displayName` removes the project prefix and a trailing numeric replica suffix (for example, `smh-sonarr-1` becomes `sonarr`). If shortening creates a collision within the same project, the affected names keep their replica suffixes. Names outside this project-prefix convention are preserved. Collision detection uses all discovered containers before filtering, so query flags do not change display names. The original `name` is always retained.

### Endpoint Provider Extension

`DockerDiscoveryService` calls the core-owned `DockerOperations` port and returns per-source snapshots and retrieval status in `DockerDiscoveryResult`. Its outbound `DockerJavaOperations` implementation maps docker-java responses into `ContainerSnapshot` records. `ContainerService` maps those discovery results with `ContainerMapper`, prepares an `EnrichmentContext` with display names resolved from each complete source list, then runs request-specific enrichment. `DisplayNameEnricher` applies the prepared names through the enrichment chain, preserving collisions involving filtered containers. Mapping and enrichment failures produce a source `ERROR` without discarding other sources. Docker metadata fields such as identity, name, and image are final, and published ports and raw labels are defensively copied into immutable collections. Enrichment fields such as `displayName`, `iconUrl`, and `urlCandidates` can be updated on the same object; the candidates setter defensively copies the supplied list. Each request creates its own containers, and mapping starts with no URL candidates.

The service then applies the `ContainerEnrichment` chain. Spring injects an ordered `List<ContainerEnricher>`; each handler receives the container and the request-specific `EnrichmentContext`, and returns `Optional<Container>`. An empty result rejects the container and stops its chain. A present result passes to the next handler; enrichment handlers update the same container instance, and filters retain it without changing it. An empty chain retains the input, and handler failures propagate to the existing exception handling.

The production handlers run in this order:

1. `ContainerStateFilter` (`@Order(0)`) rejects stopped containers unless `includeStopped` is enabled.
2. `UrlCandidateEnricher` (`@Order(100)`) discovers URL candidates through the registered endpoint providers.
3. `EndpointAvailabilityFilter` (`@Order(200)`) rejects containers without URL candidates unless `includeWithoutEndpoints` is enabled. This filter runs after discovery so label-only services remain discoverable.
4. `IconEnricher` (`@Order(250)`) assigns the icon URL from the image name using configured mappings and icon settings.
5. `VersionEnricher` (`@Order(300)`) attaches cached version information and queues a background check for an eligible cache miss without waiting for resolution.

To add filtering or enrichment, implement `ContainerEnricher` as a `@Component` in its own concern package under `core/enrichment/` and declare its order with `@Order` (for example, `@Order(400)` to run after the existing handlers). Spring includes it automatically without changes to the service or chain.

The controller passes a core `ContainerQuery` containing the query flags through the `GetContainers` inbound port. The service builds an immutable `EnrichmentContext` per source containing its ID, configured published host, `includeStopped`, and `includeWithoutEndpoints`. `PublishedPortEndpointProvider` uses `PublishedHostResolver` to resolve the configured host, defaulting to `127.0.0.1` when absent. Request hostnames and forwarded headers do not affect endpoint discovery.

The service returns a domain `ContainerInventory` containing source outcomes and domain containers. `ContainerController` maps these through `inbound/web/mapper/ContainerDTOMapper` into `inbound/web/dto/ContainerDTO` and `EndpointDTO`. The API exposes domain URL candidates as `endpoints`, keeping raw labels and published ports internal. Providers and enrichment use domain `Endpoint` values, without dependencies on API records or Jackson annotations.

`EndpointProvider` is a sealed interface permitting `PublishedPortEndpointProvider` and `TraefikEndpointProvider`, both final Spring components. To add a provider, implement the interface in a final `@Component` in the same package and add that class to its `permits` clause. `EndpointDiscovery` constructor-injects `List<EndpointProvider>`, so every permitted provider bean participates automatically without changing discovery or adding a runtime registry or provider enum. Providers return all their candidates, use namespaced stable IDs, and never select a default. Discovery deduplicates IDs and sorts by source and URL. The client groups unfamiliar source identifiers dynamically.

For example, after adding `MyProxyEndpointProvider` to the interface's `permits` clause, this illustrative provider uses an application-specific label (not a standard proxy label):

```java
@Component
@NullMarked
public final class MyProxyEndpointProvider implements EndpointProvider {
    @Override
    public List<Endpoint> discover(Container container, EnrichmentContext context) {
        String host = container.labels().get("myproxy.host");
        if (host == null || !EndpointUrls.isValidHost(host)) return List.of();
        String url = EndpointUrls.routeUrl("https", host, "/");
        return List.of(new Endpoint("my-proxy:" + url, "my-proxy", url));
    }
}
```

Place implementations and URL helpers in `core/enrichment/endpoint/`; shared endpoint values live in `core/model/`. Each enrichment handler owns a concern package: `state`, `endpoint`, `endpointavailability`, or `version`. The published hostname is bound per connection in `config/properties/DockerConnectionProperties` and passed into the source enrichment context; there is no global endpoint-host setting. New reflectively accessed types require Spring AOT binding hints; generated reachability metadata must not be edited by hand.

Published-port IDs use `published-port:<port>` independently of the configured hostname. Traefik IDs use `traefik:<normalized-url>`. The client opens the API URL directly, resolves saved IDs before Traefik defaults, and keeps automatic defaults out of storage. See [configuration](CONFIGURATION.md#endpoint-discovery) for supported literal rules, selection, and hostname policy.

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

- `core/port/inbound/` — `GetContainers`, `RefreshVersions`, and `CheckDockerAvailability`, called by inbound adapters and implemented by core services.
- `core/port/outbound/` — `DockerOperations` and `RegistryOperations`, implemented by outbound integrations.
- `core/service/` — `ContainerService` coordinates mapping, enrichment, and response assembly. `DockerDiscoveryService` owns concurrent Docker retrieval, per-source deadlines, cancellation, and retrieval failure translation. `DockerAvailabilityService` probes Docker availability with a shared deadline and per-source in-flight deduplication.
- `core/enrichment/` — the ordered `ContainerEnrichment` chain, its contract and request context; each handler has its own concern package (`state/`, `name/`, `endpoint/`, `endpointavailability/`, `icon/`, `version/`). Endpoint discovery, providers, parsing, and URL rules belong to `endpoint/`.
- `core/enrichment/icon/` — `IconEnricher` assigns icons using `IconResolver` and `IconSettings`.
- `core/enrichment/version/` — `VersionEnricher` attaches cached version results to containers.
- `core/enrichment/version/refresh/` — `VersionRefreshService` owns background scheduling, refresh coordination, retries, and cache state; `VersionRefreshSettings` supplies its interval.
- `core/enrichment/version/resolution/` — `VersionResolver` gathers version evidence; installed-version detection, retained observations, release selection, update policy, and numeric comparison live alongside it.
- `core/model/` — application-owned container/source metadata, outcomes, queries, image references, version values, and exceptions. Ports never expose SDK objects or client handles.
- `core/mapping/` — `ContainerMapper` converts discovery snapshots to core containers.
- `core/enrichment/name/` — `DisplayNameResolver` resolves source-wide name collisions; `DisplayNameEnricher` applies the names prepared in the context.
- `inbound/web/` — HTTP controllers, exception handling, wire records in `dto/`, and response mapping in `mapper/`. `ContainerController` calls `GetContainers`; `VersionController` calls `RefreshVersions`.
- `inbound/health/` — translates `CheckDockerAvailability` results into actuator health responses; probing and timeout handling belong to the core service.
- `outbound/docker/` — docker-java calls, SDK mapping, failure translation, and managed client ownership in `DockerSourceRegistry`.
- `outbound/registry/` — `RegistryOperationsAdapter` implements the core port, selects `RegistryClient` providers, and caches complete raw catalogues. `TagPagination` handles complete bounded traversal. `dockerhub/` and `ghcr/` own provider integration; `distribution/` shares authenticated tag/manifest protocol, token lifecycle, and Link parsing. Numeric selection and update decisions belong to `core.enrichment.version.resolution`.
- `config/` — Spring wiring, YAML icon loading, HTTP transport setup, and translation from bound properties to core settings.
- `config/properties/` — `@ConfigurationProperties` beans and validation. Docker Hub transport uses `porthole.registry.dockerhub.*`.
- `config/nativehints/` — Spring AOT hints for library types the agent cannot observe.

### Dependency boundaries

Core owns the interfaces and values at its boundaries. Controllers and health
adapters call inbound ports; Docker and registry integrations implement outbound
ports. Both adapter groups may depend on `config/properties/`, but not on
configuration wiring classes. Configuration wires the implementations. Core does
not import adapters, configuration-property
beans, Docker SDK classes, servlet types, or HTTP clients. Spring component and
lifecycle annotations remain allowed in core.

```text
inbound ──→ core ←── outbound
              ↑
            config wires implementations
```

Version enrichment only reads cached results and queues background work. Registry
HTTP and Docker inspection stay off the inventory request path. Refresh state,
revision guards, deduplication, and pruning retain one coordinator's ownership.
`ArchitectureTest` uses ArchUnit to check core isolation, adapter dependency
direction, and port contracts against production bytecode, while behavior
tests cover pipeline composition, source failures, and refresh races.

## Integration Test Infrastructure

`server/src/test/java/.../it/`:

- `PortholeContainer` — Testcontainers wrapper that launches the application image; supports optional native-image agent attachment for hint generation
- `IntegrationTestBase` — per-test WireMock and Porthole lifecycles; the app starts after stubbing, and background work stops before registry verification and teardown. Low-priority inventory stubs cover unrelated host containers while test-specific stubs retain strict usage checks
- `ContainerAwareIntegrationTestBase` — extends base to also start real Docker containers for end-to-end scenarios
- Tests in `controller/`, `health/`, and `resilience/` extend these bases

See [server test commands](../server/README.md#test) for component-test requirements and runtime selection.

### Architecture migration test policy

Keep every file under `server/src/test/java/com/roomelephant/porthole/it` unchanged
during structural migrations. Those tests exercise packaged application behavior
against JVM and native images. Unit tests outside that directory follow the new
production packages and test adapter translation and core behavior separately.

Integration tests use the production HTTP DTOs in `inbound/web/dto/` and shared
values in `core/model/`.
