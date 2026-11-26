# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Porthole is a Docker dashboard that auto-discovers running containers and their exposed ports. It is a monolithic application: a Spring Boot 4 backend (Java 25) that serves a React 19 frontend as embedded static resources, compiled to a GraalVM native executable for production.

`AGENTS.md` is a symlink to this file.

## Commands

Every component has its own Makefile (`client/`, `server/`, `docker/`); the root Makefile holds only cross-component targets. Run `make help` for the root targets.

### Server

```bash
make -C server test                        # unit tests
make -C server test-one TEST=DashboardProjectorTest
make -C server it                          # ITs against the existing porthole-jvm:latest image
make -C server format                      # Spotless apply — run before committing
make -C server dev                         # run the backend on the JVM, no client needed
make -C server openapi                     # regenerate docs/openapi.yml
```

Integration tests run the packaged application in Testcontainers, so they need an image. If **server source** changed, package the current server before rebuilding the image (not needed for test-only changes):

```bash
make -C server package && make -C docker jvm-image && make -C server it
# or, in one step from the repo root:
make server-it
```

To run the same IT suite against a native image, build it and select it explicitly — `porthole.it.image` defaults to `porthole-jvm:latest`:

```bash
make -C server it MVN='mvn -Dporthole.it.image=porthole-native-validation:latest'
```

### Client

```bash
make -C client dev     # Vite dev server on :5173, proxies /api and /actuator to :9753
make -C client test    # vitest run with coverage
make -C client lint
```

### Cross-component and Docker

```bash
make lab             # run the current branch against fake Docker hosts and containers (lab/), on :5173
make bundle          # client build + JAR with client in static resources
make bundle-native   # client build + GraalVM native executable (server/target/porthole)
make -C docker jvm-image   # porthole-jvm:latest, used by ITs
make -C docker test        # entrypoint scenarios; may prompt for sudo to create a mock socket
```

## Architecture

Full detail lives in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); the points below are what's hard to recover by reading one file.

### Slices

The server is split into feature slices: `fleet/`, `endpoints/`, `versions/`, `entries/` and `dashboard/`, plus `shared/` for small common rules and `nativehints/`. Each slice keeps its model, services and ports in its root package, its adapters in subpackages (`docker`, `registry`, `store`, `icons`, `web`), and owns its `@ConfigurationProperties` and `@Configuration`. `ArchitectureTest` enforces the rules with ArchUnit against production bytecode, so a layering mistake fails the unit test run:
- No cycles. `fleet` uses no other slice; `endpoints`, `versions` and `entries` use only `fleet`; `dashboard` uses the others through their root packages; every slice may use `shared`.
- Nothing depends on `dashboard`.
- A slice's root package never imports its own adapters, Docker SDK types, servlet types or HTTP clients.
- A `web` package never imports another slice's adapters.

### Sources and the dashboard

Docker state lives in memory: `fleet` keeps one `SourceMonitor` per configured connection, polling every 30 seconds and listing again soon after Docker events. A failed source keeps its last good containers for 24 hours and reports `DOWN` or `ERROR`. No HTTP request waits on Docker. Containers and cached version results use `(sourceId, containerId)`. Saved settings use the entry ID `container:<sourceId>:<containerName>`, so they survive container recreation. Include the source ID whenever identifying a container.

`GET /api/dashboard` is the client's only read. `DashboardProjector` builds it as a pure function of the fleet's snapshots, the saved entries and the version cache. It computes default names over each source's whole listing, discovers endpoints, then applies each entry's saved name, icon and endpoint choice over the defaults. The browser does all filtering. Icon URLs are built only in `dashboard/web/IconUrls`.

`DashboardChanges` bumps the dashboard's `revision` on every fleet snapshot change, entry write and version result or run, and `GET /api/events` signals it to browsers as coalesced server-sent events; the client refetches when the revision differs from its cached one and otherwise polls every 5 minutes. A new kind of change reaches browsers only if its owner tells a listener that `DashboardChanges` implements.

`EndpointProvider` (in `endpoints/`) is a sealed interface; a new provider must be a final `@Component` added to the `permits` clause, and it is then picked up automatically by constructor injection. Providers mark candidates `ROUTED` (proxy routes) or `DIRECT` (published ports), and `EndpointSelection` prefers routed ones when nothing is saved.

`VersionTracker` (in `versions/`) runs version checks in the background from snapshots; the dashboard only reads their cache, so registry HTTP and Docker inspection never run on a request.

### Entries

What users save lives in the `entries` slice: an `EntryRecord` per container (`container:<sourceId>:<containerName>`) or link (`link:<32 hex>`), written through `POST`/`PATCH`/`DELETE /api/entries` and kept in `state.json` under `PORTHOLE_DATA_DIR`. Defaults decide what a container gets automatically: `DefaultIcons` picks the default icon (for example `mongo` → `mongodb`), and endpoint discovery finds URLs. `DashboardProjector` applies a container's saved entry on top.

To add an editable field: add it to `EntryRecord`, `EntryPatch`, `EntryPatchReader` and `JsonStateStore` (which maps fields by hand), and apply it in `DashboardProjector`.

### Native image hints

GraalVM requires explicit reflection/proxy/JNI configuration. Generated hints live in:

```
server/src/main/resources/META-INF/native-image/com.roomelephant/porthole-agent/reachability-metadata.json
```

Regenerate with `make server-native-hints` after adding features that use reflection, and commit the result. Do not hand-edit the generated JSON — for types not reached by the ITs, register them manually in `nativehints/DockerNativeHints` using `RuntimeHints` or `@RegisterReflectionForBinding`.

## Key conventions

- **Format**: Spotless enforces code style. CI runs `spotless:check`; local builds skip it by default (`-Dspotless.check.skip=true`). Run `make -C server format-check` before pushing.
- **Coverage gates**: JaCoCo requires at least 80% instruction and branch coverage per class, with additional aggregate gates. See `server/pom.xml` for exact rules and exclusions, and `client/vite.config.js` for client thresholds.
- **Null safety**: `@NullMarked` / JSpecify annotations throughout.
- **Caching**: Caffeine caches registry (Docker Hub / GHCR) catalogue responses, configured via `RegistryProperties`.
- **Virtual threads**: enabled in Spring Boot for the server.
- **OpenAPI**: `docs/openapi.yml` is generated from Swagger annotations on the controllers. Regenerate and commit it with any API change — CI checks for drift.
- **IT test policy**: preserve packaged-application behavior tests under `server/src/test/java/com/roomelephant/porthole/it/` during structural refactors; they test behavior, not package layout. Update supporting infrastructure under `it/infra/` when necessary.

## CI/CD

See [CI/CD Pipelines](docs/ARCHITECTURE.md#cicd-pipelines). Three path-filtered CI workflows (`ci-server.yml`, `ci-client.yml`, `ci-docker.yml`) run on push/PR to `main`; the release workflow builds the native binary per architecture and re-runs the full IT suite against it before pushing images.

Key point: a feature can pass all JVM tests and still fail native — GraalVM AOT issues only surface when the ITs run against the native binary.

When creating PRs, apply labels from `.github/release.yml` so the release changelog is generated correctly: `feature`/`enhancement`, `fix`/`bug`, `chore`/`test`/`refactor`, `documentation`/`docs`, `dependencies`, or `ignore-for-release`.
