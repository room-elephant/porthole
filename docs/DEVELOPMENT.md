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

The native executable will be in `server/target/porthole`. First compilation takes 3-5 minutes; subsequent builds are faster with caching.

**Requirements**: GraalVM JDK 25+ must be installed and configured as your JAVA_HOME.

### Docker Image

For local development, use the multi-stage Dockerfile in `dev/`:

```bash
# From the project root
docker build -f dev/Dockerfile -t porthole:latest .

# Or use docker compose
docker compose -f dev/compose.yml up --build
```

The development Dockerfile uses a multi-stage build that automatically builds both client and server. Dependencies are cached for faster rebuild times.

For CI/production, the `docker/Dockerfile` expects a pre-built native executable (built with `make bundle-native`).

## Running Locally

### From Native Executable

```bash
./server/target/porthole
```

### From JAR

```bash
java -jar server/target/porthole-0.0.1-SNAPSHOT.jar
```

### With Docker

```bash
docker run -p 9753:9753 -v /var/run/docker.sock:/var/run/docker.sock porthole:latest
```

Access the application at [http://localhost:9753](http://localhost:9753)

## Testing

### Server Tests

Run the Spring Boot backend tests:

```bash
make -C server test    # Unit tests
make -C it             # Integration tests with coverage report at server/target/site/jacoco/index.html
```

#### JVM vs Native Integration Tests

The integration test suite runs twice in the pipeline, each time against a different runtime:

| | CI (`reusable-server.yml`) | Release (`native-it` job) |
|---|---|---|
| **Runtime** | JVM JAR (`Dockerfile.it`) | Native binary (`docker/Dockerfile`) |
| **Trigger** | Every push / PR to `main` | Every release tag |
| **Purpose** | Catch logic and API bugs fast | Catch AOT/native-image failures |

A feature can pass all JVM tests and still fail in native. GraalVM AOT compilation requires explicit configuration for reflection, proxies, and JNI — any class or method not registered will be missing at runtime. These failures are invisible on the JVM because the JVM resolves everything dynamically.

Running the same IT suite against the native binary surfaces missing reflection config, incomplete AOT hints, or `ClassNotFoundException`s before the image is pushed. Both runs are necessary; neither substitutes for the other.

### Client Tests

Run the React client unit tests:

```bash
cd client
npm test              # Watch mode
npm run test:run      # Single run
npm run test:coverage # With coverage report
```

### Docker Entrypoint Tests

The project includes a dedicated script to test the Docker entrypoint logic (e.g., dynamic socket group handling). This script builds a test image and runs scenarios to verify correct permission handling.

```bash
make -C docker test
```

> [!NOTE]
> Running this script locally may prompt for your `sudo` password to create and set permissions for a mock Docker socket.

## Development Workflow

For active development, you can run the client and server separately:

1. **Start the backend** (from `server/`):
   ```bash
   mvn spring-boot:run
   ```

Or use IntelliJ IDEA Pre-configured run configurations available in `server/.run/`:

- **Porthole java local** - Runs the application locally
- **Porthole container** - Remote debugging for containerized application (port 5005)

2. **Start the client dev server** (from `client/`):
   ```bash
   npm run dev
   ```

The client dev server proxies API requests to the backend.

## GitHub Actions Workflows

### CI Workflow

Runs on push/PR to `main`. Detects which parts of the codebase changed and only runs relevant jobs:

- **Server job**: Builds and tests the Spring Boot backend (skipped if no `server/` changes)
- **Client job**: Builds and tests the React frontend (skipped if no `client/` changes)
- **Docker job**: Verifies the Docker image build (skipped if no `docker/` changes)

The server job runs integration tests against the **JVM-based JAR** (`Dockerfile.it`). This gives fast feedback on business logic and API correctness during development.

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


