# docker

Production Docker configuration. The `Dockerfile` expects a pre-built native executable; it does not compile the application.

| File | Purpose |
|------|---------|
| `Dockerfile` | Production image (CI/release) — wraps a pre-built native binary |
| `Dockerfile.jvm` | JVM image — runs the JAR; used by integration tests and local Docker testing |
| `entrypoint.sh` | Handles Docker socket group reconciliation and privilege drop at runtime |
| `templates/` | Default configuration files copied into the image |

## Test the entrypoint

```bash
make test
```

Builds a test image and runs entrypoint scenarios (socket permissions, group handling). May prompt for `sudo` to create a mock socket.

## Docs

- [Building the Docker image](../docs/DEVELOPMENT.md#docker-image)
- [Docker entrypoint tests](../docs/DEVELOPMENT.md#docker-entrypoint-tests)
- [Runtime design & permissions](../docs/ARCHITECTURE.md#docker-runtime-design)
