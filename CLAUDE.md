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
make -C server test-one TEST=ContainerServiceTest
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
make bundle          # client build + JAR with client in static resources
make bundle-native   # client build + GraalVM native executable (server/target/porthole)
make -C docker jvm-image   # porthole-jvm:latest, used by ITs
make -C docker test        # entrypoint scenarios; may prompt for sudo to create a mock socket
```

## Architecture

Full detail lives in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); the points below are what's hard to recover by reading one file.

### Hexagonal boundaries

`server/src/main/java/com/roomelephant/porthole/` is split into `core/`, `inbound/`, `outbound/`, and `config/`.

```text
inbound ──→ core ←── outbound
              ↑
            config wires implementations
```

Core owns the ports (`core/port/inbound/`, `core/port/outbound/`) and all boundary values in `core/model/`. Core must not import adapters, Docker SDK types, servlet types, HTTP clients, or configuration wiring classes — Spring component/lifecycle annotations are allowed in core. Adapters may depend on `config/properties/` but not on configuration wiring. `ArchitectureTest` enforces this with ArchUnit against production bytecode, so a layering mistake fails the unit test run.

### Multi-source discovery

Every inventory request fans out across all configured Docker connections concurrently. A per-source failure degrades that source (`UP`/`DOWN`/`ERROR` in the `sources` array) and never fails the whole response — a completed inventory returns 200 even when all sources failed. Containers, cached version results, and refresh state use `(sourceId, containerId)`. Client-side container preferences use `(sourceId, containerName)` so they survive container recreation. Include the source ID whenever identifying a container.

### Enrichment chain

`ContainerService` runs an ordered chain of `ContainerEnricher` beans once per source batch, listed explicitly in `EnrichmentConfiguration` (display name → state filter → URL candidates → endpoint availability filter → icon → version). Order matters: display-name collision detection runs on the full inventory *before* filters, and endpoint discovery runs before the availability filter so label-only services stay discoverable. To add a stage, add a `@Component` in its own concern package under `core/enrichment/` and insert it into that explicit list — `PipelineWiringTest` checks every handler bean appears in the chain exactly once.

`EndpointProvider` is a sealed interface; a new provider must be a final `@Component` added to the `permits` clause, and it is then picked up automatically by constructor injection.

Version enrichment only reads cached results and queues background work — registry HTTP and Docker inspection never run on the inventory request path.

### Native image hints

GraalVM requires explicit reflection/proxy/JNI configuration. Generated hints live in:

```
server/src/main/resources/META-INF/native-image/com.roomelephant/porthole-agent/reachability-metadata.json
```

Regenerate with `make server-native-hints` after adding features that use reflection, and commit the result. Do not hand-edit the generated JSON — for types not reached by the ITs, register them manually in `config/nativehints/DockerNativeConfig` using `RuntimeHints` or `@RegisterReflectionForBinding`.

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
