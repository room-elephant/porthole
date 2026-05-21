FROM debian:bookworm-slim

LABEL org.opencontainers.image.title=Porthole
LABEL org.opencontainers.image.description="Centralized dashboard for running Docker containers with automatic port discovery."
LABEL org.opencontainers.image.source=https://github.com/Room-Elephant/porthole
LABEL org.opencontainers.image.licenses=MIT

RUN apt-get update && apt-get install -y wget gosu && rm -rf /var/lib/apt/lists/*

RUN groupadd -g 65532 nonroot && \
    useradd -u 65532 -g nonroot -s /bin/false nonroot

WORKDIR /app

COPY --chown=nonroot:nonroot porthole porthole
COPY --chown=nonroot:nonroot docker/templates/ /app/config/
COPY --chmod=755 docker/entrypoint.sh /app/entrypoint.sh

EXPOSE 9753

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:9753/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh", "./porthole", "--spring.config.additional-location=file:/app/config/"]
