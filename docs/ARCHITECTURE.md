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
- **State**: React Query inventory with shared endpoint choices; LocalStorage for layout and update notifications
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

The profile prepares an empty recording directory before the tests. Preparation refuses to clear a directory that still holds a `.lock` from an earlier run — failing with `Previous native hint recordings are still locked` — rather than deleting output an agent may still be writing. `PortholeContainer` attaches the native-image agent whenever the profile supplies `native.agent.output.dir`. Recording instances stop gracefully so their agents flush metadata; a JUnit launcher-session listener stops the shared recording instance before JVM shutdown and Testcontainers cleanup.

`PortholeContainer` bind-mounts each `app-<random>` recording directory to the agent's `/tmp/native-hints` output path. The host directory is made world-readable, writable, and executable so resilience containers running as UID 65532 can write to it. The agent flushes every ten seconds (`config-write-period-secs=10`) and on graceful shutdown. Periodic writes limit data loss after a crash, but recordings with an unreleased `.lock` are still rejected.

After Failsafe verifies that the tests passed, `NativeHintsMerger` checks every instance produced metadata and released its lock. It runs the official `native-image-configure generate` tool from `ghcr.io/graalvm/native-image-community:25` with every recording as an input. The merged metadata is staged separately and replaces the source-tree `reachability-metadata.json` only after successful merging. Failed tests, missing recordings, active locks, or merge failures leave the published metadata unchanged. Locks are never deleted to bypass an active agent.

Regular JVM and native IT runs do not supply the recording property and therefore do not attach an agent. The JVM test image provides the agent; the separate GraalVM tool image provides merging. Hint generation requires Java 25, Maven, and Docker, without requiring a host GraalVM installation. The raw recordings and merged intermediate outputs remain under `server/target/` for inspection.

### Spring AOT hints

`DockerNativeHints` (`server/src/main/java/.../nativehints/DockerNativeHints.java`) registers additional Spring AOT hints for Docker SDK model, command, and configuration-file types used reflectively by Jackson. This covers types that the agent cannot observe because they are never instantiated during the IT run.

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
- **Provider composition** uses concrete `DistributionClient` and `BearerTokenClient` collaborators. Each provider owns separate repository-scoped token state while sharing expiry, anonymous token decoding, and one refresh/retry after an authenticated request returns 401. Both providers send tag-list and manifest requests with the anonymous token. Private-package login and Docker credential discovery are outside this feature.
- **Catalogue pagination** parses Distribution `tags[]` and `Link` headers for both providers; Docker Hub asks for 1000 tags per page and GHCR for 100. Docker Hub's website API isn't used because it refuses anonymous clients after 1000 tags. `TagPagination` follows complete same-origin/path catalogues with cycle, page-count, and elapsed-budget checks. It returns raw tags without selecting versions. `LinkHeaderParser` handles Distribution pagination metadata.
- **Numeric releases** are selected by `ReleasePolicy` in `versions`, using arbitrary-precision numeric components, optional lowercase `v`, zero-padding, and deterministic alias ties. The existing dotted-numeric grammar and all-major-version policy are documented in [Registry Configuration](CONFIGURATION.md#registry-configuration).
- **Manifest facts** come from authenticated HEAD requests negotiating OCI image/index and Docker v2 image/list media types. `DistributionClient` returns validated digest and manifest-kind metadata. Core treats exact equality as unchanged and different same-algorithm digests with remotely confirmed matching manifest levels as changed. Missing old manifests, unknown types, and incompatible levels remain inconclusive. This compares repository manifests; a changed multi-platform index does not necessarily mean the installed platform changed. Platform-aware resolution is deferred.
- **Independent evidence** distinguishes available, missing, unsupported, and unavailable remote lookups. `RegistryRequests` logs each unavailable lookup at WARN with its resource and reason (the HTTP status when the registry refused). A failed catalogue lookup does not prevent manifest checking, and a failed manifest lookup does not erase a newer release. Either positive channel establishes an update; incomplete required evidence is internally inconclusive. The public `VersionInfo` JSON shape remains unchanged.
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
| `/api/dashboard`                        | GET    | Every source's state and every entry, unfiltered, with the revision and version-check state; always 200 |
| `/api/events`                           | GET    | Server-sent `changed` events carrying `{"revision":N}`; 503 beyond 100 open streams          |
| `/api/entries`                          | POST   | Adds a link from `{name, url, stack?, icon?}`; 201 with the resolved entry, 507 at 500 links |
| `/api/entries/{id}`                     | PATCH  | JSON Merge Patch of `name`, `icon`, `endpoint` and `stack`; 200 with the resolved entry, 204 for a container no source lists |
| `/api/entries/{id}`                     | DELETE | Deletes a link; 405 for a container                                                         |
| `/api/icons`                            | POST   | Stores a raw PNG, WebP, JPEG or SVG body of at most 1 MB; 201 `{id}`                        |
| `/api/icons/{id}`                       | GET    | Serves an uploaded icon with an immutable cache lifetime and `nosniff`                      |
| `/api/sources`                          | GET    | Status and `lastSuccessAt` of every configured Docker source; always 200                    |
| `/api/version-checks`                   | POST   | Starts a version check of every container in the background; 202                           |
| `/actuator/health`                      | GET    | Health of Porthole itself; Docker status is not included                                    |

### Entries

An entry is what the user saved about one tile: a container, identified by `container:<sourceId>:<containerName>`, or a link, identified by `link:<32 hex>`. The `entries` slice owns the model (`EntryRef`, `EntryRecord`, `Icon`, `EndpointChoice`), keeps every record in an immutable in-memory `EntryState`, and is the only writer of the [data directory](CONFIGURATION.md#data-directory). Source IDs and Docker names can't contain `:`, so IDs parse unambiguously; a malformed ID names no entry and is a 404.

A container record holds only overrides of what discovery supplies: `name`, `icon` and `endpoint`. A record with nothing left is removed. A link record holds its own `name`, a `{url}` endpoint and an optional `stack`. `PATCH /api/entries/{id}` is a JSON Merge Patch: a missing field keeps its value and `null` removes it, which on a container restores the discovered value and on a link's name or URL is a 400. A container's stack comes from Docker Compose, so `stack` on a container is a 400. A container can be patched while it isn't running, as long as its source is configured; links are deleted with `DELETE`, containers can't be.

`EntryService` runs writes one at a time. Each builds the next `EntryState`, saves it through the `StateStore` port, and publishes it only after the save succeeded, so a failed write leaves memory and file as they were. `JsonStateStore` maps every field by hand to and from `state.json` with a `schemaVersion`, using `AtomicFiles` to replace the whole file. It loads once at startup; a newer `schemaVersion` or an invalid file fails startup. A record for a source that is no longer configured is kept.

The write endpoints live in `dashboard/web` rather than `entries/web` because they answer with the entry as the dashboard now shows it, and nothing outside the `dashboard` slice may depend on it.

### Uploaded Icons

Icons upload separately from the entry that uses them: `POST /api/icons` takes the raw image as the body and returns an ID, which a `PATCH` or link `POST` then saves as `{"upload": id}`. The ID is the SHA-256 of the stored bytes plus the extension, so the same image is stored once and responses never change. An upload is identified from its first bytes, ignoring its name and declared type. The body is read up to 1 MB plus one byte, so a chunked request can't make Porthole buffer more. At 500 stored files a new upload gets 507; re-uploading an existing image doesn't count against the limit.

Uploaded SVGs are treated as hostile. `SvgSanitizer` rebuilds each one from an allowlist of elements and attributes before it is stored, dropping scripts, event handlers, embedded HTML, animation and references outside the document, with DTD processing off. A file with nothing left to draw is rejected, and the ID is the hash of the sanitized bytes. `GET /api/icons/{id}` also sends a sandboxing `Content-Security-Policy`, so an icon opened directly as a page cannot run anything.

Because one file can back several entries, a file is never deleted when an entry stops using it. `IconSweeper` removes files that no entry references and that haven't been used for an hour, at startup and every 15 minutes. Saving an upload on an entry, and re-uploading the same image, refresh the file's age, so a sweep can't remove an icon between its upload and its save. The dashboard sends each entry's icon as `{url, choice}`: `choice` is the saved `{"library": name}` or `{"upload": id}`, or null for the default, so the client never reads meaning out of a URL.

### External Links

Links are pages Porthole doesn't run, such as a router. They are entries, saved and edited through `/api/entries` like containers, and the dashboard lists them after every container. Because the dashboard is read from memory, a slow or failed Docker host can't delay or hide a link. Link URLs follow the same http(s) rule as custom URLs (`HttpUrls`), and at most 500 links are stored, because these endpoints have no login.

The External source exists only in the client: it isn't in the `sources` array, and it appears when there is at least one link. Its filter value `_external` can't clash with a Docker source ID, which can't start with `_`. In Stack view a link joins the section whose name exactly matches its stack, after the containers.

`App` owns the Link settings dialog rather than the tile, because saving a stack or name can move the tile to another group, which would unmount a dialog the tile owned. The Add form holds a chosen icon until the link exists, then saves it as a second request. If that fails the link stays, and Link settings opens with an alert.

### Dashboard

`GET /api/dashboard` is the one read the client makes. It returns every configured source and every entry:

```json
{
  "revision": 1762596000042,
  "versionChecks": { "running": false, "lastCompletedAt": "2026-11-08T09:00:00Z" },
  "sources": [{ "id": "walker", "name": "Walker", "status": "UP", "error": null, "lastSuccessAt": "2026-11-08T10:00:00Z" }],
  "entries": [{
    "id": "container:walker:smh-sonarr-1", "kind": "container", "name": "sonarr", "stack": "smh",
    "icon": { "url": "https://cdn.jsdelivr.net/.../sonarr.webp", "choice": null },
    "endpoints": [{ "id": "traefik:https://sonarr.example/", "source": "traefik", "url": "https://sonarr.example/", "selected": true }],
    "hasWebUi": true,
    "container": { "sourceId": "walker", "name": "smh-sonarr-1", "defaultName": "sonarr", "image": "linuxserver/sonarr:4",
      "state": "running", "status": "Up 2 hours", "self": false, "version": null }
  }]
}
```

It includes every container a source lists or still retains after a failure, stopped ones and ones without endpoints too, in configuration and listing order, followed by links in the order they were saved. The browser filters by state, endpoints, host and search text. The request reads only memory, so it never waits on Docker, a registry or the disk.

`DashboardProjector` builds the response as a pure function of three inputs: the fleet's snapshots, the saved `EntryState`, and the version cache. For each container it:

1. computes the default name with `DisplayNames` over the source's whole listing, so filters never change names;
2. discovers endpoint candidates with the `endpoints` slice;
3. applies the saved entry: its name, its icon, and its endpoint choice over the automatic one;
4. picks the default icon with `DefaultIcons`, or Porthole's own logo for the container Porthole runs in;
5. attaches the cached version for `(sourceId, containerId)`.

Icon URLs are built only in `dashboard/web/IconUrls`.

### Live Updates

`DashboardChanges` keeps the dashboard's `revision`. It listens to fleet snapshots, entry writes (`EntryListener`), and version results and version-check runs (`VersionListener`), and bumps the revision on each change. Revisions start at the server's start time in epoch milliseconds, so a restarted server never repeats one a browser has cached. `DashboardService` reads the revision before the other inputs, so a dashboard always reflects at least the changes its revision counts.

`GET /api/events` is a server-sent event stream that signals changes without carrying them:

```text
event:changed
data:{"revision":1762596000042}
retry:5000
```

Each stream opens with the current revision. Changes are coalesced over 250 ms, a `:keepalive` comment follows every 15 seconds, and `retry: 5000` asks browsers to reconnect after 5 seconds. `EventStreams` allows at most 100 open streams and answers 503 beyond that. It closes every stream when the application stops, before the web server's graceful shutdown waits on in-flight requests.

The browser refetches the dashboard when an event's revision differs from the one it holds, which also covers reconnecting after a restart. A failed stream is reopened after 30 seconds, and the dashboard is also polled every 5 minutes, so a lost stream only delays changes.

At DEBUG, `DashboardChanges` logs each revision with its cause, for example `Dashboard revision 1762596000042: lab-a: restarting state running → restarting` or `entry container:walker:app saved: …`. `make lab` turns it on; elsewhere set `LOGGING_LEVEL_COM_ROOMELEPHANT_PORTHOLE_DASHBOARD_DASHBOARDCHANGES=DEBUG`.

For names matching `<project>-<name>`, the default name drops the project prefix and a trailing numeric replica suffix (for example, `smh-sonarr-1` becomes `sonarr`). If that makes two names in one project collide, both keep their replica suffixes. A saved name replaces the default name without taking part in collisions; `container.defaultName` always reports the computed one, and `container.name` the Docker name.

A source whose latest listing succeeded is `UP`, including an empty list. Transport failures and deadlines are `DOWN`; other errors are `ERROR`. Diagnostic causes stay in server logs.

### Endpoints

The `endpoints` slice turns a container's labels and published ports into `Candidate(id, source, url, kind)` values. `EndpointProvider` is sealed: `TraefikEndpointProvider` produces `ROUTED` candidates from router rules, and `PublishedPortEndpointProvider` produces `DIRECT` ones from published ports using the source's configured published host (default `127.0.0.1`). Request hostnames and forwarded headers never affect them. A new provider must be a final `@Component` added to the `permits` clause.

`EndpointSelection.automatic` picks what opens when nothing is saved. It considers `ROUTED` candidates if there are any, otherwise `DIRECT` ones, and selects the only one, else the only one at the root path, else none. The dashboard then applies the saved choice:
- A discovered choice selects its candidate while that candidate exists, and falls back to automatic selection while it doesn't, without rewriting the file.
- A custom URL appears as a selected `custom` endpoint.
- `"none"` deselects everything and makes `hasWebUi` false.

Each entry's `endpoints` holds zero or one selected endpoint, and `hasWebUi` is true when there are endpoints and the choice isn't `"none"`.

### Fleet

The `fleet` slice keeps every source's state in memory. `Fleet` owns one `SourceMonitor` per configured source.
- **Listing:** a monitor lists containers on start, every `porthole.docker.poll-interval` (default `30s`), and about 500 ms after any container event from Docker's event stream; a burst of events causes one listing.
- **Polls:** polls for one source never overlap, and each listing is bounded by the source's connection timeout plus response timeout.
- **Events:** events only signal a fresh listing and are never applied as changes. When the stream fails or is blocked, the monitor reconnects with backoff from 1 to 30 seconds, and polling continues.
- **Retention:** a failed listing keeps the source's last good containers until `porthole.docker.snapshot-retention` (default `24h`) has passed since its last success.
- **Startup:** before its first listing a source is `DOWN` with "Waiting for first check".

`SnapshotListener` beans are told about each snapshot whose content changed. `GET /api/sources` exposes the same state and always returns 200.

### Versions

`VersionTracker` is a `SnapshotListener`.
- **When it checks:** it checks new containers, and containers whose image ID changed, as soon as a snapshot shows them. It re-checks the rest from the latest snapshot every `porthole.versions.refresh-interval` (default `1h`), and never lists containers itself.
- **Workers:** four shared workers inspect containers and images through their owning Docker client.
- **Keys and pruning:** results, retry state and in-flight work use `(sourceId, containerId)`. Only an `UP` snapshot prunes that source's missing containers.
- **Never blocking:** successful results stay cached while refreshing, and the dashboard only reads the cache.

Registry catalogue caches are keyed by normalized registry and repository. A failed catalogue lookup and manifest reads are shared for one minute, so replicas of one image checked together cost one registry request. Installed version and digest comparisons stay specific to each container.

`POST /api/version-checks` returns `202 Accepted` and starts a check across every source. It bypasses the refresh interval and invalidates the registry catalogue cache in the background. A request while a check runs does nothing. Existing results stay visible while checks run. The dashboard's `versionChecks` reports `running` until every source's checks finish, then `lastCompletedAt`; each changed result and the run's start and end bump the revision.

Health endpoints report only Porthole itself. The image's `HEALTHCHECK` calls `/actuator/health/liveness`, so an unreachable Docker host never marks the Porthole container unhealthy.

### Shared Endpoint Choices

A container's endpoint choice is the `endpoint` field of its entry: `{"id": candidate}` for a discovered endpoint, `{"url": url}` for a custom URL, or `"none"`. A discovered choice stores only the candidate ID, which already names its provider, never a resolved URL. Clients save a choice with `PATCH /api/entries/{id}`, and `"endpoint": null` restores automatic selection.

The browser keeps one `['dashboard']` query. After a successful PATCH, a dashboard GET confirms the resolved state; the PATCH response confirms storage, and the browser never navigates from it. Older reads are canceled so stale results can't undo the confirmation. Save controls serialize changes and stay pending through confirmation. If the PATCH succeeds and the GET fails, settings report that the choice was saved but couldn't be refreshed, and retry repeats only the GET. Layout and update notifications stay in browser storage, keyed by entry ID. See [the data directory](CONFIGURATION.md#data-directory) for paths, named volumes, and nonroot bind-mount permissions.

## Directory Structure

```
.
├── client/             # React frontend (Vite + React 19)
├── server/             # Spring Boot backend (Java 25)
├── docker/             # Docker configuration (see docker/README.md)
└── docs/               # Documentation
```

## Server Package Layout

`server/src/main/java/com/roomelephant/porthole/` is split into feature slices. Each slice keeps its model, services and ports in its root package, its adapters in subpackages, and its own `@ConfigurationProperties` and `@Configuration`.

- `fleet/` — configured sources (`SourceCatalog`), the `DockerOperations` port, `Fleet` and its per-source `SourceMonitor`s, `SourceSnapshot` and `SnapshotListener`, and `SelfContainer` with its resolver.
  - `fleet/docker/` — docker-java calls, container events, SDK mapping, failure translation, and managed client ownership in `DockerSourceRegistry`.
  - `fleet/web/` — `GET /api/sources`.
- `endpoints/` — the sealed `EndpointProvider` and its providers, Traefik rule parsing, URL rules, `Candidate` and `EndpointSelection`.
- `versions/` — `VersionTracker` (background and manual checks, their `VersionChecks` state, retries and the result cache, told about containers by snapshots), `VersionResolver` with installed-version detection, retained observations, release selection, update policy and numeric comparison, the version and registry-evidence values, and the `RegistryOperations` port.
  - `versions/registry/` — `RegistryOperationsAdapter` implements the port, selects `RegistryClient` providers, and caches complete raw catalogues. `TagPagination` handles complete bounded traversal. `dockerhub/` and `ghcr/` own provider integration; `distribution/` shares the authenticated tag and manifest protocol, token lifecycle and Link parsing. `RegistryProperties` (`porthole.registry.*`) and the HTTP client wiring live here.
  - `versions/web/` — `POST /api/version-checks`.
- `entries/` — the entry model, `EntryService`, `IconService`, `IconSweeper`, `SvgSanitizer`, and the `StateStore` and `IconFileStore` ports.
  - `entries/store/` — `state.json` reading and writing.
  - `entries/icons/` — uploaded icon files on disk, named by content hash.
  - `entries/web/` — `/api/icons`.
- `dashboard/` — `DashboardProjector`, `DisplayNames`, `DefaultIcons`, `DashboardService`, `DashboardChanges`, and the dashboard's properties and icon mappings.
  - `dashboard/web/` — `GET /api/dashboard`, `GET /api/events` with `EventStreams`, the `/api/entries` write endpoints, and `IconUrls`.
- `shared/` — small rules several slices use: `Hosts`, `HttpUrls`, `AtomicFiles` and `ImageReference`; `shared/web/` holds the global exception handler.
- `nativehints/` — Spring AOT hints for library types the native-image agent cannot observe.

### Dependency boundaries

```text
dashboard ──→ fleet, endpoints, versions, entries
endpoints ──→ fleet
versions  ──→ fleet
entries   ──→ fleet
fleet     ──→ (no slice)
every slice may use shared; shared and nativehints use no slice
```

`ArchitectureTest` checks these rules against production bytecode with ArchUnit:

- Slices have no cycles, and nothing depends on `dashboard`.
- `dashboard` reaches other slices only through their root packages.
- A slice's root package never imports its own adapter subpackages (`docker`, `registry`, `store`, `icons`, `web`), nor Docker SDK, servlet, `java.net.http` or Spring web and HTTP types. Ports keep SDK objects and client handles out of slice roots.
- A `web` package never imports another slice's adapters.

The dashboard only reads cached version results; registry HTTP and Docker inspection never run on a request.

## Integration Test Infrastructure

`server/src/test/java/.../it/`:

- `PortholeContainer` — Testcontainers wrapper that launches the application image; supports optional native-image agent attachment for hint generation
- `IntegrationTestBase` — per-test WireMock and Porthole lifecycles; the app starts after stubbing, and background work stops before registry verification and teardown. Low-priority inventory stubs cover unrelated host containers while test-specific stubs retain strict usage checks
- `ContainerAwareIntegrationTestBase` — extends base to also start real Docker containers for end-to-end scenarios
- Tests in `controller/`, `health/`, and `resilience/` extend these bases

See [server test commands](../server/README.md#test) for component-test requirements and runtime selection.

### Integration test policy

Integration tests under `server/src/test/java/com/roomelephant/porthole/it` exercise packaged application behavior against JVM and native images, so they survive structural refactors; only `it/infra/` follows package changes. They read the API as JSON (`it/infra/DashboardView`) rather than through production response types. Unit tests follow the production packages.
