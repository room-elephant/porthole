# Configuration

[← Back to README](../README.md)

## Docker Connections

Porthole connects to every entry in `porthole.docker.connections` and groups their
containers by host, then Docker Compose project. The default is one source named
`Local` using `unix:///var/run/docker.sock`.

Mount an application configuration with a connection list to use several sources:

```yaml
porthole:
  docker:
    connections:
      - id: walker
        name: Walker
        host: unix:///var/run/docker.sock
        published-host: docker.example.com
        connection-timeout: 5s
        response-timeout: 60s
      - id: mike
        name: Mike
        host: unix:///remote/mike.sock
        published-host: docker2.example.com
        connection-timeout: 5s
        response-timeout: 60s
```

The second socket is an example of an endpoint already made available to the
Porthole process. Porthole does not create remote connections through SSH itself;
provision remote access separately when deploying. Unix socket paths must exist
inside the Porthole container and be accessible to its runtime user. The existing
entrypoint only adjusts group membership for `/var/run/docker.sock`; permissions
for other mounted sockets must be provisioned externally.

A configured list replaces the packaged default. IDs must be unique and match
`[a-z0-9][a-z0-9_-]*`; names must be nonblank. IDs identify sources in the API,
version caches, and container preferences, so keep them stable when renaming hosts.
Changing a source ID creates a new preference scope. Entries support absolute
`unix:///path/to/socket` and `tcp://hostname:port` URIs through the existing Docker
transport; `ssh://` is not a supported application URI. This feature adds no
per-source TLS certificate or credential settings.

Each entry defaults to a `5s` connection timeout and `60s` response timeout, even
when omitted from an external list. Timeouts must be positive. Their sum bounds
Docker container retrieval for that source; presentation and enrichment run outside
that deadline. Each entry can set `published-host` to the hostname/IP clients should
use for direct port links. It must omit scheme, port, and path. When omitted,
it defaults to `127.0.0.1` for that connection. Traefik-discovered URLs retain their own hosts.

Restart Porthole after changing connections. An unavailable source does not stop
the application from starting: its host section reports the failure and retries
on the next 30-second dashboard poll. Empty lists and invalid entries fail
startup, rather than silently falling back to the local daemon.

### Environment Variables

Configure connections with indexed environment variables:

| Variable | Description |
| --- | --- |
| `PORTHOLE_DOCKER_CONNECTIONS_0_ID` | Required unique source ID |
| `PORTHOLE_DOCKER_CONNECTIONS_0_NAME` | Required display name |
| `PORTHOLE_DOCKER_CONNECTIONS_0_HOST` | Required Docker socket or TCP URI |
| `PORTHOLE_DOCKER_CONNECTIONS_0_PUBLISHEDHOST` | Hostname or IP for published-port links; defaults to `127.0.0.1` |
| `PORTHOLE_DOCKER_CONNECTIONS_0_CONNECTIONTIMEOUT` | Connection timeout; defaults to `5s` |
| `PORTHOLE_DOCKER_CONNECTIONS_0_RESPONSETIMEOUT` | Response timeout; defaults to `60s` |

Additional entries use indexes `1`, `2`, and so on. Supply every entry's required
fields; higher-priority lists replace lower-priority defaults. Each entry may set
`published-host`; omitted values default to `127.0.0.1`. Unknown YAML properties under
`porthole.docker` are rejected by strict binding.

To set the address for published-port links, provide a complete connection:

```bash
docker run -d \
  -p 9753:9753 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e PORTHOLE_DOCKER_CONNECTIONS_0_ID=local \
  -e PORTHOLE_DOCKER_CONNECTIONS_0_NAME=Local \
  -e PORTHOLE_DOCKER_CONNECTIONS_0_HOST=unix:///var/run/docker.sock \
  -e PORTHOLE_DOCKER_CONNECTIONS_0_PUBLISHEDHOST=docker.example.com \
  --name porthole \
  ghcr.io/room-elephant/porthole:latest
```

Replace `docker.example.com` with your Docker host's IP address or hostname. Environment
variables replace the connection list, so `ID`, `NAME`, and `HOST` must accompany
`PUBLISHEDHOST`. Setting `PUBLISHEDHOST` alone prevents startup.

Shared endpoint choices and browser update-warning preferences are scoped by source ID
and container name.

### Porthole's Own Container

`GET /api/containers` marks the container Porthole runs in with `"self": true`.
At startup Porthole resolves its own container ID, first hit wins: the
`porthole.self-container-id` property, a full ID from `/proc/self/mountinfo`, a
full ID from `/proc/self/cgroup`, then `HOSTNAME` when it is 12 to 64 lowercase
hex characters. The resolved ID is logged at startup.

A full 64-character ID matches on any source, including a `tcp://` socket proxy.
A shorter ID, such as Docker's default hostname, matches as a prefix only on
`unix://` sources.

Set `PORTHOLE_SELFCONTAINERID` only when detection fails: typically the container
hostname is overridden (`hostname:` in Compose) and `/proc` does not expose the
container ID. Use the full ID when Porthole reaches its own daemon over `tcp://`.

## Configuration Files

Application settings share the `porthole` namespace: `porthole.docker`, `porthole.versions`, `porthole.dashboard`, `porthole.registry`, and `porthole.self-container-id`. Spring's `server`, `spring`, and `management` settings keep their standard namespaces.

For compound property names, Spring's canonical environment form removes hyphens: for example, `porthole.registry.cache.version-max-size` becomes `PORTHOLE_REGISTRY_CACHE_VERSIONMAXSIZE`.

The Docker image includes template configuration files in `/app/config/`:

| File              | Purpose                                                                 |
|-------------------|-------------------------------------------------------------------------|
| `application.yml` | Main application settings (Docker connections, registry timeouts, icon CDN)    |
| `icons.yml`       | Custom icon mappings                                                    |

You can mount your own files to override these templates. You can either mount the entire directory or individual files:

```bash
# Mount the entire config directory
docker run -d -p 9753:9753 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v $(pwd)/config:/app/config \
  --name porthole porthole

# OR mount individual files
docker run -d -p 9753:9753 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v $(pwd)/my-icons.yml:/app/config/icons.yml \
  --name porthole porthole
```

### Background Version Checks

Version checks start asynchronously when Porthole is ready and run for all containers, including stopped containers and those without endpoints. Results are held in memory and included in `GET /api/containers` as a nullable `version` object. Requests read cached results immediately; they do not wait for version inspection or registry HTTP calls. A newly discovered container queues a background check if it has no cached result.

```yaml
porthole:
  versions:
    refresh-interval: 1h
```

Set `PORTHOLE_VERSIONS_REFRESHINTERVAL` to override the interval through the environment. It must be positive. The interval is the delay after a completed background inventory refresh, with one immediate refresh at startup. Up to four containers are checked concurrently, and duplicate checks for the same container are coalesced.

Before a successful check, `version` is `null`. Completed results contain `currentVersion`, `latestVersion`, and `updateAvailable`; either version string may be null when metadata is unavailable. During refreshes, prior results remain visible. Each successful registry evidence channel is retained independently during failures, so cached information may be stale. A successful negative observation clears a previous positive in that channel. Evidence is discarded when the configured image reference or installed image ID changes, or matching repository provenance is lost; Docker inspection failures retain the previous result. Failed first checks retry no sooner than the configured interval. Version results are keyed by `(sourceId, containerId)`, so the same image or container ID on different hosts retains independent installed-version and update results. Docker container and image inspection uses the owning source. Registry release catalogues remain shared by normalized registry/repository. Each source scans independently; a failed source scan keeps its cached results and cannot prune another source. Deleted containers are removed from that source’s cache after its successful inventory scan; restarting Porthole clears the cache.

The client retrieves updated cached information through its existing 30-second container polling. Its per-container notification preference controls warning visibility; it does not stop server-side checks.

### Registry Configuration

Porthole checks public images on Docker Hub and GitHub Container Registry (GHCR). Docker Hub accepts familiar names such as `nginx` and explicit `docker.io` references; GHCR requires `ghcr.io/<owner>/<image>`, including nested image paths. Other registries, local image IDs, digest-pinned images, and locally retagged images without matching repository provenance are skipped. Private packages and GitHub source repository/release tags are not supported. No GitHub login or token configuration is required for public GHCR images. Registry URL overrides change the transport destination, not the image registry identity. You can configure timeouts and cache settings:

| Property                          | Default | Description                           |
|-----------------------------------|---------|---------------------------------------|
| `PORTHOLE_REGISTRY_TIMEOUT_CONNECT`        | `5s`    | Connection timeout for registry APIs |
| `PORTHOLE_REGISTRY_TIMEOUT_READ`           | `10s`   | Read timeout for registry APIs       |
| `PORTHOLE_REGISTRY_CACHE_TTL`              | `1h`    | TTL for complete registry tag catalogues |
| `PORTHOLE_REGISTRY_CACHE_VERSIONMAXSIZE` | `100` | Maximum cached registry tag catalogues   |

The container refresh interval and `PORTHOLE_REGISTRY_CACHE_TTL` control separate layers. A refresh can reuse a registry catalogue while its TTL has not elapsed; lower the TTL as well if you need fresher catalogue results more often. Container results themselves do not expire on this TTL.

Docker Hub and GHCR have independent transport settings, useful with a trusted transport proxy:

| Property | Default |
|---|---|
| `PORTHOLE_REGISTRY_DOCKERHUB_REGISTRYURL` | `https://registry-1.docker.io/v2/` |
| `PORTHOLE_REGISTRY_DOCKERHUB_AUTHURL` | `https://auth.docker.io/token?service=registry.docker.io&scope=repository:` |
| `PORTHOLE_REGISTRY_DOCKERHUB_REPOSITORIESURL` | `https://hub.docker.com/v2/repositories/` |
| `PORTHOLE_REGISTRY_GHCR_REGISTRYURL` | `https://ghcr.io/v2/` |
| `PORTHOLE_REGISTRY_GHCR_AUTHURL` | `https://ghcr.io/token?service=ghcr.io&scope=repository:` |

In YAML, Docker Hub settings live under `porthole.registry.dockerhub` with `registry-url`, `auth-url`, and `repositories-url`, matching GHCR's `porthole.registry.ghcr` naming.

The registry base ends in `/`; the auth prefix ends in `repository:` and receives the repository name plus `:pull`. Overrides must preserve these API shapes. Both providers use anonymous pull tokens whose lifetime follows the authentication response, independently of catalogue caching. An authenticated registry request returning `401` triggers one token refresh and retry. Porthole does not read Docker credentials. Complete tag catalogues, including empty ones, are cached by normalized registry and repository. Manifest observations remain fresh on each check. Inaccessible packages, denied requests, rate limits, and incomplete responses supply unavailable evidence. Catalogue and manifest checks run independently; positive evidence from either can establish an update. See [GitHub's Container registry documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).

Numeric release checks accept tags with at least two dot-separated integer components, optionally prefixed by `v` (for example, `1.2`, `v1.2.3`). Components are compared numerically without integer overflow; missing trailing components count as zero. Suffixes such as `-alpine`, prereleases, and single-number tags are excluded. The highest numeric tag across all major versions is considered; there is no major-version pinning policy. A warning requires a strictly newer comparable version, and this release check only applies when the configured image tag is numeric.

Docker Hub tag enumeration follows JSON `next` links; GHCR uses OCI tag-list `Link` headers. Both follow pagination on the same catalogue URL, with a fixed limit of 100 pages of up to 100 tags each. `PORTHOLE_REGISTRY_TIMEOUT_READ` also supplies the total enumeration budget, checked between responses; an individual in-flight request still uses its configured HTTP timeout. Failed, unsafe, cyclic, or incomplete enumeration supplies unavailable catalogue evidence rather than publishing a partial maximum; it does not prevent the independent manifest check. The enumeration budget is not a hard deadline for the entire check: authentication and manifest requests use their individual timeouts.

Mutable tag changes are checked independently of numeric releases. Digests must belong to the same repository, use the same hash algorithm, and represent the same manifest level (image manifest or multi-platform index). Missing or incompatible metadata is internally inconclusive; index-versus-platform-image differences alone never trigger a warning. The current comparison concerns repository manifests: another platform changing can change a multi-platform index, and removal of an old manifest can prevent comparison. Platform-aware image comparison is deferred. The update tooltip says **Image update available**, since a changed tag does not necessarily identify a newer numeric release.

### Icon Mappings

Porthole uses a smart icon resolution strategy, but sometimes you need manual control. You can configure icon mappings using a YAML file.

#### Icon CDN

By default, icons are fetched from Dashboard Icons CDN. You can customize the source:

| Property                          | Default                                                         | Description                           |
|-----------------------------------|-----------------------------------------------------------------|---------------------------------------|
| `PORTHOLE_DASHBOARD_ICONS_URL`             | `https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp`  | Base URL for icons                    |
| `PORTHOLE_DASHBOARD_ICONS_EXTENSION`       | `.webp`                                                         | File extension                        |

#### Icon Resolution Logic

1.  **Exact Match**: Checks `icons.yml` (both internal defaults and your external overrides).
2.  **Dashboard Icons**: Fetches from the configured CDN using the image name.

#### Customizing Icons

You can override defaults or add new mappings by editing `/app/config/icons.yml` or mounting your own file.

1. Create YAML File

```yaml
# Maps container image names to Dashboard Icons slugs
my-custom-redis: redis
postgres: postgresql
custom-app: react
```

The **Key** is the container image name (or simple name like `postgres`).
The **Value** is the icon slug from [Dashboard Icons](https://github.com/homarr-labs/dashboard-icons).

2. Mount the File

Mount your file to `/app/config/icons.yml` as described in the [Configuration Files](#configuration-files) section.

#### Default Mappings

The application comes with these built-in defaults:

| Image Name  | Mapped Icon Slug    |
| :-----------| :------------------ |
| `mongo`     | `mongodb`           |
| `postgres`  | `postgresql`        |
| `cassandra` | `apache-cassandra`  |

Overriding these keys in your external file will take precedence.

## Endpoint Discovery

Porthole lists containers with at least one discovered endpoint or a saved custom URL by default. **Show containers without a web UI** includes containers with no usable URL; **Show stopped containers** is independent. Containers without endpoints still have settings and version controls.

### Published-Port Hostname

Published-port URLs use HTTP and the connection’s `published-host`, falling back to `127.0.0.1` when it is omitted. For example, setting `published-host: docker.example.com` produces `http://docker.example.com:51413/` for a container publishing port 51413. Port 80 uses the standard `http://docker.example.com/` form. IPv6 addresses are bracketed correctly.

Set `published-host` on each Docker connection to the address clients should use for its published ports, including remote Docker hosts and reverse-proxy deployments:

```yaml
porthole:
  docker:
    connections:
      - id: local
        name: Local
        host: unix:///var/run/docker.sock
        published-host: docker.example.com
```

The first connection's environment variable is **`PORTHOLE_DOCKER_CONNECTIONS_0_PUBLISHEDHOST`**. Use the [complete connection example](#environment-variables) when configuring it through environment variables. Its value must be a bare hostname or IP address, without a scheme, port, or path. Invalid values prevent application startup. Every connection defaults to `127.0.0.1` when `published-host` is omitted. Request hostnames and forwarded headers do not affect published-port URLs. Porthole does not use the browser's client IP or a container bridge address.

Direct ports are candidates, not guarantees of a web UI: a published torrent, database, or HTTPS-only port can still produce an HTTP candidate. Choose the appropriate endpoint in settings.

### Traefik Labels

Discovery reads resolved Docker container labels such as:

```yaml
labels:
  traefik.http.routers.app.rule: 'Host(`app.example.com`)'
  traefik.http.routers.app.tls: 'true'
  traefik.http.routers.files.rule: 'Host(`files.example.com`) && PathPrefix(`/dl/`)'
  traefik.http.routers.files.tls: 'true'
```

These yield `https://app.example.com/` and `https://files.example.com/dl/`. A Host-only router and a path router remain separate choices; renamed routers with identical URLs are deduplicated. Label keys are matched case-insensitively. `traefik.enable=false` suppresses Traefik discovery while published-port candidates remain.

Supported rules contain one literal `Host` and optionally one literal `Path` or `PathPrefix`, joined by `&&`, with grouping parentheses and backtick or double-quoted strings. The whole rule must be supported. OR, negation, regex matchers, multiple arguments, additional predicates, unresolved substitutions, and malformed rules are skipped. No Traefik connection or reachability probe is needed.

Router `tls=true`, or a TLS sublabel such as `tls.certresolver`, implies HTTPS; explicit `tls=false` wins, and absent TLS implies HTTP. Global entrypoint TLS defaults, custom external proxy ports, and middleware effects are not inferred. Backend service ports/schemes and entrypoint names do not determine the browser URL. Traefik candidates use standard external HTTP/HTTPS ports.

## Per-Container Settings

Each container has a settings panel accessible via the gear icon. Endpoint choices are shared by every browser through the Porthole server. Layout and update-notification preferences remain in your browser's local storage.

### URL Selection

The **Tile opens** section lets you choose a discovered URL, **Custom URL**, or **No web UI**. Choose a discovered URL or enter and save an absolute HTTP or HTTPS custom URL without credentials. Typing a URL alone does not save it. A custom URL works even without published ports or Traefik labels and keeps the container visible under the normal endpoint filter. Porthole stores the URL without fetching it. **No web UI** disables launching; settings and update checking remain available.

A saved endpoint ID takes precedence. Otherwise, Porthole selects the sole Traefik URL or the unique root `/` route among several Traefik URLs. If Traefik is absent, it selects a sole candidate from any provider. Multiple root routes, multiple non-root Traefik routes, or multiple candidates without Traefik open settings for a choice.

Automatic selection applies when no choice is saved; the update API restores it with an explicit null selection. If a saved discovered endpoint disappears, selection follows the same automatic policy while preserving its identity for when it returns. An explicit **No web UI** choice remains in effect when discovered URLs change. Restoring automatic selection, choosing a discovered endpoint, or choosing **No web UI** replaces a custom selection and permanently removes its URL; choosing custom again requires entering it again. Save errors leave the last confirmed selection in effect.

Endpoint choices are keyed by `(sourceId, containerName)`, so containers with the same name on different sources stay independent. Recreating a container with the same name retains its choice; renaming it starts a new preference identity. Choices for temporarily absent containers remain saved. Existing browser endpoint choices are not automatically migrated because devices may disagree; select your endpoint once after upgrading. Other browsers receive saved choices on their next inventory request or page reload, without additional preference polling.

### Preference File and Persistence

`porthole.preferences.path` (environment variable **`PORTHOLE_PREFERENCES_PATH`**) selects the shared JSON file. Local development defaults to `data/preferences.json`, relative to the server's working directory. Both Docker images default to `/app/data/preferences.json`. Mount a named volume to retain choices when replacing the application container:

```yaml
services:
  porthole:
    image: ghcr.io/room-elephant/porthole:latest
    ports:
      - "9753:9753"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - porthole-data:/app/data

volumes:
  porthole-data:
```

The image creates `/app/data` owned by its application user (UID/GID `65532`), including when Docker initializes an empty named volume. For a host bind mount, provision a directory writable by that user and any existing file readable by it; atomic saves also require directory write permission. The entrypoint does not change ownership of arbitrary host files. Keep the volume across container restarts and replacements; deleting it deletes the saved choices.

One running Porthole instance owns each file; do not share a writable preference file between instances. Saves atomically replace the entire file, with memory updated only after a successful write. A missing file starts with no choices. An unreadable or malformed existing file fails startup rather than discarding saved data. The server loads the file once at startup: manual external edits require a server restart. The file contains container records with `sourceId`, `containerName`, and `preferences.selectedEndpoint`, without a schema version field; it is separate from application YAML configuration.

### Version Update Notifications

Use **Show update notifications** to show or hide a container's update warning (⚠️). The preference is stored in your browser. Version checks continue on the server regardless of this setting. This is useful for hiding notifications on containers you do not intend to update.
