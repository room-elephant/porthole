# Development Guide

This guide covers everything you need to build, test, and run Porthole locally.

For deeper technical details on how Porthole works, see the [Architecture](ARCHITECTURE.md) documentation.

## Prerequisites

- Java 25 and Maven
- Node.js 24.15+ and npm
- Docker

## Trying the App Locally

To run whichever branch you have checked out against a realistic setup:

```bash
make lab
```

When it prints `Ready`, open [http://localhost:5173](http://localhost:5173). Frontend edits appear instantly; restart `make lab` to pick up server changes. Press Ctrl-C to stop the app and remove the fake containers. Downloaded images stay cached, so later starts are quick. The first run also needs internet to download them.

The lab only needs Docker, Java, Maven and Node. It never connects to your real Docker, so your own containers stay out of it. Everything it uses lives in [`lab/`](../lab):

| Path | Purpose |
|------|---------|
| `compose.yml` | The two fake Docker hosts, each its own Docker-in-Docker daemon. They run privileged and are only reachable from your machine. |
| `scenarios/lab-a.yml` | Container states: healthy, unhealthy, health starting, restarting, paused, stopped, created. |
| `scenarios/lab-b.yml` | Endpoints, names and versions: published ports, no ports, Traefik labels, colliding replicas, an outdated image. |
| `application.yml` | Porthole's sources: `lab-a`, `lab-b`, and `lab-down`, which is unreachable and always shows as `DOWN`. |
| `data/state.json` | Saved container settings and links, tracked in git: `Router` and `NAS` in the `stack-a` stack, and `google` with none. The app writes here directly, as it does for uploaded icons in `data/icons/`, so use `git restore` to undo changes you don't want to keep. |
| `data/icons/` | Uploaded icons, tracked in git. Each is named by the SHA-256 of its content and must be used by an entry in `state.json`; otherwise the app removes it after an hour, so uploading through the UI is easier. |

Containers without endpoints, such as `no-ports`, and stopped ones are hidden until you turn them on in the dashboard's settings. Traefik isn't running, so Traefik URLs appear in the UI but may not open.

To delete the cached images: `docker volume rm porthole-lab_lab-a-data porthole-lab_lab-b-data`.

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
mvn -f server/pom.xml verify -Popenapi
```

The `openapi` Maven profile uses the Springdoc Maven plugin to generate YAML. It starts a temporary JVM application with the Spring `openapi` profile, fetches `/v3/api-docs.yaml` into `server/target/openapi/openapi.yml`, stops the application, and copies the completed export to `docs/openapi.yml` during `verify`. A failed export fails the build after shutdown and preserves the existing documentation. Spring Boot automatically loads `server/src/main/resources/application-openapi.yml` for this profile; it holds the HTTP settings, Springdoc settings, and temporary Docker connection. Docker is not required. Maven retains the build lifecycle, output path, and shared HTTP/JMX ports; the HTTP port is filtered into `application-openapi.yml`.

The Maven profile skips tests and formatting checks by default; CI runs them separately. Override with `-DskipTests=false -Dspotless.check.skip=false` when combining those checks with generation.

Document operations, query parameters, and responses using Swagger annotations on the controllers. The application defines the API title, version, and relative server URL with `@OpenAPIDefinition`. Swagger annotations are a direct dependency; the Springdoc WebMVC generator is included only by the Maven `openapi` profile. Normal JVM and native packages do not contain the generator or expose API documentation endpoints. Generation requires both the Maven profile to include Springdoc and the Spring `openapi` profile to enable its endpoints; the command above activates both.

Commit the regenerated specification with API changes. CI regenerates it and checks for drift. The temporary HTTP and JMX ports can be overridden with `-Dopenapi.port=...` and `-Dopenapi.jmx-port=...`.

## Regenerating Native Image Hints

Run this after adding new features that use reflection, proxies, or JNI. Requires Java 25, Maven, and Docker. The recording agent and merge tool run in GraalVM containers.

```bash
make server-native-hints
```

Every IT application instance records separately under `server/target/native-hints/`. After the tests pass, GraalVM merges all recordings into
`server/src/main/resources/META-INF/native-image/com.roomelephant/porthole-agent/reachability-metadata.json`; incomplete recordings fail generation rather than silently losing coverage.

Commit the updated `reachability-metadata.json` and rebuild the native image. If a class isn't reached by the ITs, register it explicitly in `nativehints/DockerNativeHints` using `RuntimeHints` or `@RegisterReflectionForBinding` rather than editing the generated file by hand.

See [Native Image Hints](ARCHITECTURE.md#native-image-hints) for how generation works.


