# docker

Production Docker configuration. The `Dockerfile` expects a pre-built native executable; it does not compile the application.

| File | Purpose |
|------|---------|
| `Dockerfile` | Production image (CI/release) — wraps a pre-built native binary |
| `Dockerfile.jvm` | JVM image — runs the JAR; used by integration tests and local Docker testing |
| `entrypoint.sh` | Handles Docker socket group reconciliation and privilege drop at runtime |
| `templates/` | Default configuration files copied into the image |

## Debug the JVM container

Run the JVM image with JDWP exposed on port 5005, then attach with the **Porthole container** IntelliJ run config:

```bash
make bundle
docker build -f docker/Dockerfile.jvm -t porthole:jvm .
docker run -p 9753:9753 -p 5005:5005 \
  -e JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  porthole:jvm
```

## Test the entrypoint

```bash
make test
```

Builds a test image and runs entrypoint scenarios (socket permissions, group handling). May prompt for `sudo` to create a mock socket.

## Docs

- [Building the Docker image](../docs/DEVELOPMENT.md#docker-image)
- [Runtime design & permissions](../docs/ARCHITECTURE.md#docker-runtime-design)
- [JVM vs Native integration tests](../docs/ARCHITECTURE.md#jvm-vs-native-integration-tests)
