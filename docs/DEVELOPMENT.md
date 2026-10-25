# Development Guide

This guide covers everything you need to build, test, and run Porthole locally.

For deeper technical details on how Porthole works, see the [Architecture](ARCHITECTURE.md) documentation.

## Prerequisites

- Java 25 and Maven
- Node.js 24.15+ and npm
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


## Regenerating the OpenAPI Specification

The generated [OpenAPI specification](openapi.yml) documents the REST API. Regenerate it after changing controllers or response DTOs:

```bash
make -C server openapi
# Equivalent Maven command:
mvn -f server/pom.xml verify -Popenapi -DskipTests -Dspotless.check.skip=true
```

The `openapi` Maven profile uses the Springdoc Maven plugin to generate YAML. It starts a temporary JVM application with the Spring `openapi` profile, fetches `/v3/api-docs.yaml` into `server/target/openapi/openapi.yml`, stops the application, and copies the completed export to `docs/openapi.yml` during `verify`. A failed export fails the build after shutdown and preserves the existing documentation. Spring Boot automatically loads `server/src/main/resources/application-openapi.yml` for this profile; it holds the HTTP settings, Springdoc settings, and temporary Docker connection. Docker is not required. Maven retains the build lifecycle, output path, and shared HTTP/JMX ports; the HTTP port is filtered into `application-openapi.yml`.

Document operations, query parameters, and responses using Swagger annotations on the controllers. The application defines the API title, version, and relative server URL with `@OpenAPIDefinition`. The Springdoc WebMVC starter supplies the annotations transitively, so no separate Swagger annotations dependency is needed. API documentation endpoints are disabled by default and enabled by the Spring `openapi` profile for generation.

Commit the regenerated specification with API changes. CI regenerates it and checks for drift. The temporary HTTP and JMX ports can be overridden with `-Dopenapi.port=...` and `-Dopenapi.jmx-port=...`.

## Regenerating Native Image Hints

Run this after adding new features that use reflection, proxies, or JNI. Requires Java 25, Maven, and Docker. The recording agent and merge tool run in GraalVM containers.

```bash
make server-native-hints
```

Every IT application instance records separately under `server/target/native-hints/`. After the tests pass, GraalVM merges all recordings into
`server/src/main/resources/META-INF/native-image/com.roomelephant/porthole-agent/reachability-metadata.json`; incomplete recordings fail generation rather than silently losing coverage.

Commit the updated `reachability-metadata.json` and rebuild the native image. If a class isn't reached by the ITs, register it explicitly in `DockerNativeConfig` using `RuntimeHints` or `@RegisterReflectionForBinding` rather than editing the generated file by hand.

See [Native Image Hints](ARCHITECTURE.md#native-image-hints) for how generation works.


