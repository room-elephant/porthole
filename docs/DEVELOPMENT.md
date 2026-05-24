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

### Full Application (Native)

Build a GraalVM native executable:

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


## Regenerating Native Image Hints

Run this after adding new features that use reflection, proxies, or JNI. Requires GraalVM JDK 25+ as `JAVA_HOME`.

```bash
make server-native-hints
```

Commit the updated `reachability-metadata.json` and rebuild the native image. If a class isn't reached by the ITs, register it explicitly in `DockerNativeConfig` using `RuntimeHints` or `@RegisterReflectionForBinding` rather than editing the generated file by hand.

See [Native Image Hints](ARCHITECTURE.md#native-image-hints) for how generation works.


