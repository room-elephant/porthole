# server

Spring Boot 4 / Java 25 backend. Compiles to a GraalVM native executable with the client bundled as static resources.

## Quick start

```bash
mvn spring-boot:run   # JVM mode, no client required
```

## Build

```bash
make package   # JAR (no client)
make native    # GraalVM native executable (no client, server only)
```

## Test

```bash
make test               # unit tests
make server-it -C ../   # integration tests (run from repo root)
```

## Docs

- [Building the server](../docs/DEVELOPMENT.md#server-only)
- [Running locally](../docs/DEVELOPMENT.md#running-locally)
- [Server tests & native ITs](../docs/DEVELOPMENT.md#server-tests)
- [Regenerating native image hints](../docs/DEVELOPMENT.md#regenerating-native-image-hints)
- [Backend tech stack & API endpoints](../docs/ARCHITECTURE.md#tech-stack)
