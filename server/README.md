# server

Spring Boot 4 / Java 25 backend. Compiles to a GraalVM native executable with the client bundled as static resources.

## Quick start

```bash
make dev   # JVM mode with local profile, no client required
```

## Build

```bash
make package   # JAR (no client)
make native    # GraalVM native executable (no client, server only)
```

## Test

```bash
make test                        # unit tests
make test-one TEST=MyTestClass   # single test class
make it                          # integration tests against JVM JAR (no Docker image build)
make -C ../ server-it            # build IT Docker image + run integration tests (run from repo root)
```

## Format

```bash
make format        # apply Spotless formatting (run before committing)
make format-check  # check only (what CI runs)
```

## IntelliJ IDEA

Run configurations are available in `.run/`:

- **Porthole java local** — runs the backend locally
- **Porthole container** — attaches remote debugger to a container exposing port 5005
- **Integration Tests** — runs all tests in `com.roomelephant.porthole.it` package

## Docs

- [Building the server](../docs/DEVELOPMENT.md#building-from-source)
- [Regenerating native image hints](../docs/DEVELOPMENT.md#regenerating-native-image-hints)
- [Native image hints](../docs/ARCHITECTURE.md#native-image-hints)
- [Backend tech stack & API endpoints](../docs/ARCHITECTURE.md#tech-stack)
