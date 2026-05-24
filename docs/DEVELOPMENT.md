# Development Guide

This guide covers everything you need to build, test, and run Porthole locally.

For deeper technical details on how Porthole works, see the [Architecture](ARCHITECTURE.md) documentation.

## Prerequisites

- Java 25 and Maven
- Node.js 24+ and npm
- Docker

## Building from Source

For building a single component in isolation:

- [`client/README.md`](../client/README.md) — React frontend
- [`server/README.md`](../server/README.md) — Spring Boot backend

The sections below cover cross-component builds.

### Full Application (JAR)

Build the complete application with client bundled into the backend JAR:

```bash
make bundle
```

The client will be automatically built and copied into the JAR's static resources.

### Native Image

Build a GraalVM native executable for faster startup and lower memory usage:

```bash
make bundle-native
```

The native executable will be in `server/target/porthole`.

**Requirements**: GraalVM JDK 25+ must be installed and configured as your JAVA_HOME.

### Docker Image

For local Docker testing, build the JVM image — fast to build, no native compilation required:

```bash
make bundle                                              # build JAR with client
docker build -f docker/Dockerfile.jvm -t porthole:jvm . # build JVM image
docker run -p 9753:9753 -v /var/run/docker.sock:/var/run/docker.sock porthole:jvm
```

For a production-equivalent image, use `make bundle-native` then `docker/Dockerfile`.

Access the application at [http://localhost:9753](http://localhost:9753)

## Testing

For test commands, see the per-component READMEs:

- [`server/README.md`](../server/README.md) — unit tests, integration tests
- [`client/README.md`](../client/README.md) — React unit tests
- [`docker/README.md`](../docker/README.md) — entrypoint tests

### JVM vs Native Integration Tests

The integration test suite runs twice in the pipeline, each time against a different runtime:

| | CI (`reusable-server.yml`) | Release (`native-it` job) |
|---|---|---|
| **Runtime** | JVM JAR (`Dockerfile.jvm`) | Native binary (`docker/Dockerfile`) |
| **Trigger** | Every push / PR to `main` | Every release tag |
| **Purpose** | Catch logic and API bugs fast | Catch AOT/native-image failures |

A feature can pass all JVM tests and still fail in native. GraalVM AOT compilation requires explicit configuration for reflection, proxies, and JNI — any class or method not registered will be missing at runtime. These failures are invisible on the JVM because the JVM resolves everything dynamically.

Running the same IT suite against the native binary surfaces missing reflection config, incomplete AOT hints, or `ClassNotFoundException`s before the image is pushed. Both runs are necessary; neither substitutes for the other.

## Development Workflow

For active development, you can run the client and server separately:

1. **Start the backend** (from `server/`): `make dev`
2. **Start the client dev server** (from `client/`): `make dev`

The client dev server proxies API requests to the backend.

IntelliJ IDEA run configurations are available in `server/.run/`:

- **Porthole java local** — runs the backend locally
- **Porthole container** — remote debugging for containerized app (port 5005)

## GitHub Actions Workflows

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
- **Manifest job**:
  - Creates a multi-arch Docker manifest combining the images.
  - Pushes the final version tag (and `latest` for stable releases).
  - Creates the GitHub Release with auto-generated notes.


The Docker image is published to GitHub Container Registry at `ghcr.io/room-elephant/porthole`.

See `.github/workflows/release.yml` for the full workflow definition.

## Regenerating Native Image Hints

Native-image hint files are auto-generated by running the integration test suite with the GraalVM native-image agent. Run this after adding new features that use reflection, proxies, or JNI:

```bash
mvn verify -Pgenerate-native-hints -f server/pom.xml
```

The agent runs inside the IT container and writes `reachability-metadata.json` directly to `server/src/main/resources/META-INF/native-image/com.roomelephant/porthole/`. Commit the updated file and rebuild the native image.

**Requirements**: GraalVM JDK 25+ must be installed and set as `JAVA_HOME` (same requirement as native builds).

### Troubleshooting missing hints

If the native binary throws `ClassNotFoundException` or `InvalidDefinitionException` for a class not reached by the ITs, register it explicitly in `DockerNativeConfig` using `RuntimeHints` or `@RegisterReflectionForBinding` rather than editing the generated file by hand.


