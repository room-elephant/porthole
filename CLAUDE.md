# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Porthole is a Docker dashboard that auto-discovers running containers and their exposed ports. It is a monolithic application: a Spring Boot backend (Java 25) that serves a React frontend as embedded static resources, compiled to a GraalVM native executable for production.

## Commands

Run a single server test class:
```bash
make -C server test-one TEST=ContainerServiceTest
```

Server integration tests — if server code changed, rebuild the JVM image first:
```bash
make -C docker jvm-image   # only when server source changed, not for test-only changes
make -C server it
```

Before committing server changes, apply formatting:
```bash
make -C server format
```

## Architecture

For server package layout and integration test infrastructure, see [Architecture](docs/ARCHITECTURE.md).

### Native image hints

GraalVM requires explicit reflection/proxy/JNI configuration. Hints live in:
```
server/src/main/resources/META-INF/native-image/com.roomelephant/porthole/reachability-metadata.json
```

Regenerate with `make server-native-hints` after adding features that use reflection. For types not reached by ITs, register them manually in `DockerNativeConfig` using `RuntimeHints` or `@RegisterReflectionForBinding` — do not hand-edit the generated JSON.

### Key conventions

- **Format**: Spotless enforces code style. CI runs `spotless:check`; local builds skip it by default (`-Dspotless.check.skip=true`). Run `make format-check` before pushing.
- **Null safety**: The codebase uses `@NullMarked` / JSpecify annotations throughout.
- **Caching**: Caffeine is used for registry (Docker Hub) response caching, configured via `RegistryProperties`.
- **Virtual threads**: Enabled in Spring Boot for the server.

### CI/CD

See [CI/CD Pipelines](docs/ARCHITECTURE.md#cicd-pipelines) in ARCHITECTURE.md. Key point: a feature can pass all JVM tests and still fail native — GraalVM AOT issues are only caught by running ITs against the native binary.
