# IT Infrastructure: Docker-in-Docker → Standard Testcontainers

**Date:** 2026-05-20  
**Status:** Approved

## Goal

Replace the complex Docker-in-Docker (DinD) IT infrastructure with standard Testcontainers running directly on the host Docker daemon. Primary driver: simplicity and maintainability. Tests run mostly in CI where host daemon isolation is acceptable.

## File Inventory

| File | Action |
|------|--------|
| `infra/DockerInfrastructure.java` | Delete |
| `infra/DinDContainer.java` | Delete |
| `infra/PortholeContainer.java` | Rewrite as standard `GenericContainer` subclass |
| `infra/IntegrationTestBase.java` | Rewrite — static containers, embedded WireMock, no DinD |
| `resilience/DockerConnectionFailureIT.java` | Rewrite — host socket scenarios, no DinD |
| `health/DockerHealthIT.java` | Minor edit — comment out `testDockerHealthWithDockerUnreachable` |
| `infra/RunWithoutContainers.java` | Unchanged |
| `controller/ContainersEndpointIT.java` | Unchanged |
| `controller/VersionEndpointIT.java` | Unchanged |
| `.github/workflows/reusable-server.yml` | Simplify — remove DinD caching, add `docker build` step |

## Architecture

All containers run on the host Docker daemon. Porthole mounts `/var/run/docker.sock` and sees all containers on that daemon — acceptable for CI. Testcontainers' Ryuk handles cleanup automatically.

WireMock runs as an **embedded in-process server** (`WireMockServer`), not a container. This keeps the container list clean for tests that assert on what Porthole sees. Porthole reaches the embedded WireMock via `host.docker.internal:<port>` — Testcontainers adds `--add-host=host.docker.internal:host-gateway` automatically on Linux.

## Component Designs

### `PortholeContainer`

A `GenericContainer<PortholeContainer>` subclass. Constructor takes the WireMock port.

- Image tag from `PORTHOLE_IT_IMAGE_TAG` env var
- `withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock")`
- Exposes port 9753
- `Wait.forHttp("/actuator/health")` — replaces hand-rolled polling loop
- Env vars:
  - `PORTHOLE_DOCKER_HOST=unix:///var/run/docker.sock`
  - `REGISTRY_URLS_REGISTRY=http://host.docker.internal:<wireMockPort>/v2/`
  - `REGISTRY_URLS_AUTH=http://host.docker.internal:<wireMockPort>/auth?...`
  - `REGISTRY_URLS_REPOSITORIES=http://host.docker.internal:<wireMockPort>/v2/repositories/`
  - `REGISTRY_CACHE_TTL=1ms`
  - `REGISTRY_CACHE_VERSION_MAX_SIZE=1`
- `baseUrl()` returns `http://localhost:<mappedPort>`

### `IntegrationTestBase`

Static initializer:
1. Start `WireMockServer` on a random port
2. Start `PortholeContainer` (static, shared across subclasses)
3. Start test containers as static `GenericContainer`s:
   - **Running busybox** (`busybox:1.37.0-uclibc`, port 8080, named `porthole-test-app`)
   - **Stopped busybox** (`busybox:1.37.0-uclibc`, port 8081, named `porthole-test-stopped`) — started via Testcontainers then immediately stopped so it exists but is not running
   - **Local image container** — built on the host using Testcontainers' `ImageFromDockerfile` with a single `FROM busybox:1.37.0-uclibc` Dockerfile, tagged `my-local-image:1.0`, then started as a container named `porthole-test-local`
   - **No-ports busybox** (`busybox:latest`, no exposed ports, named `porthole-test-no-ports`)

No manual shutdown hooks — Ryuk handles cleanup. No custom `DockerClient`. No `removeContainerQuietly`.

`pauseDocker()` / `unpauseDocker()` removed — no longer referenced.

`@AfterEach` WireMock stub validation unchanged.

### `DockerConnectionFailureIT`

One `WireMockServer` for the class. Each nested class starts/stops its own `PortholeContainer` in `@BeforeEach` / `@AfterEach`.

**`WhenSocketMissing`:** Porthole started with `PORTHOLE_DOCKER_HOST=unix:///tmp/missing-docker.sock`. No file at that path.

**`WhenPermissionDenied`:** `@BeforeEach` creates a temp file via `Files.createTempFile`, sets permissions to 000 with `Files.setPosixFilePermissions(path, Set.of())`, bind-mounts it into Porthole. `@AfterEach` deletes the temp file.

### `DockerHealthIT`

`testDockerHealthWithDockerUnreachable` commented out with a note to revisit. The test requires a socket that is present but stops responding mid-run — no clean host-based solution identified yet.

## Image Building

The Porthole Docker image is built outside of the test code:

1. CI builds the native binary (cached by source hash)
2. CI runs `docker build -t porthole-it:latest -f docker/Dockerfile .` from the repo root
3. `mvn verify -Pintegration-tests` runs with `PORTHOLE_IT_IMAGE_TAG=porthole-it:latest`

`PortholeContainer` reads `PORTHOLE_IT_IMAGE_TAG` and fails fast if the env var is not set.

## CI Changes (`reusable-server.yml`)

**Removed steps:**
- Cache Host Docker Images (`docker:dind`, `ryuk`)
- Load Cached Docker Images
- Pull and Save Docker Images
- Cache Docker-in-Docker images
- Fix cache permissions

**Removed env vars on integration test step:**
- `CI: true`
- `DOCKER_CACHE_PATH`

**Added step** (after "Build native binary", before "Run integration tests"):
```yaml
- name: Build integration test Docker image
  working-directory: ${{ github.workspace }}
  run: docker build -t porthole-it:latest -f docker/Dockerfile .
```

**Added env var on integration test step:**
```yaml
env:
  PORTHOLE_IT_IMAGE_TAG: porthole-it:latest
```
