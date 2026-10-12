# Configuration

[← Back to README](../README.md)

## Docker Host

By default, Porthole connects to Docker via `unix:///var/run/docker.sock`. You can override this using an environment variable:

```bash
docker run -d -p 9753:9753 \
  -e DOCKER_HOST=tcp://localhost:2375 \
  --name porthole porthole
```

## Configuration Files

The Docker image includes template configuration files in `/app/config/`:

| File              | Purpose                                                                 |
|-------------------|-------------------------------------------------------------------------|
| `application.yml` | Main application settings (Docker host, registry timeouts, icon CDN)    |
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

### Registry Configuration

Porthole queries Docker Hub to check for updates. You can configure timeouts and cache settings:

| Property                          | Default | Description                           |
|-----------------------------------|---------|---------------------------------------|
| `REGISTRY_TIMEOUT_CONNECT`        | `5s`    | Connection timeout for Docker Hub API |
| `REGISTRY_TIMEOUT_READ`           | `10s`   | Read timeout for Docker Hub API       |
| `REGISTRY_CACHE_TTL`              | `1h`    | How long to cache version information |
| `REGISTRY_CACHE_VERSION_MAX_SIZE` | `100` | Maximum cached version entries   |

### Icon Mappings

Porthole uses a smart icon resolution strategy, but sometimes you need manual control. You can configure icon mappings using a YAML file.

#### Icon CDN

By default, icons are fetched from Dashboard Icons CDN. You can customize the source:

| Property                          | Default                                                         | Description                           |
|-----------------------------------|-----------------------------------------------------------------|---------------------------------------|
| `DASHBOARD_ICONS_URL`             | `https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp`  | Base URL for icons                    |
| `DASHBOARD_ICONS_EXTENSION`       | `.webp`                                                         | File extension                        |

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

Porthole lists containers with at least one discovered endpoint by default. **Show containers without endpoints** includes containers with no usable URL; **Show stopped containers** is independent. Tiles without endpoints are disabled, but their gear icon and version settings remain available.

### Published-Port Hostname

Published-port URLs use HTTP and the hostname from the incoming Porthole request. For example, accessing `http://192.168.50.6:9753/` produces `http://192.168.50.6:51413/` for a container publishing port 51413. Port 80 uses the standard `http://192.168.50.6/` form. IPv6 addresses are bracketed correctly.

Use an override when Porthole's browser hostname differs from the Docker host that publishes the ports, including remote Docker hosts or reverse-proxy deployments:

```yaml
porthole:
  endpoints:
    published-host: docker.example.com
```

The environment variable is **`PORTHOLE_ENDPOINTS_PUBLISHEDHOST`**. Its value must be a bare hostname or IP address, without a scheme, port, or path. Invalid values prevent application startup. The default is unset; the request hostname is resolved for each request. Porthole does not use the browser's client IP or a container bridge address.

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

Each container has a settings panel accessible via the gear icon. Settings are stored in your browser's local storage.

### URL Selection

The **Endpoint** section contains one selection across **No endpoint**, **Traefik URLs**, **Published Ports**, and any additional providers. Choose a URL to launch from the tile, or **No endpoint** for a container without a web UI. No endpoint greys out the tile and disables launching; the gear icon remains available to choose a URL again. Version Updates is a separate section, and update checking remains available for tiles with no endpoint.

A saved endpoint ID takes precedence. Otherwise, Porthole selects the sole Traefik URL or the unique root `/` route among several Traefik URLs. If Traefik is absent, it selects a sole candidate from any provider. Multiple root routes, multiple non-root Traefik routes, or multiple candidates without Traefik open settings for a choice.

Automatic defaults are not saved. If a saved endpoint disappears, selection follows the same automatic policy. An explicit **No endpoint** choice is saved and remains in effect when discovered URLs change. Existing numeric port preferences migrate when their published port still exists and no new endpoint preference has been saved; the old preference remains intact. Preferences synchronize across browser tabs.

### Version Update Checking

You can enable or disable version update checking for individual containers. When disabled:
- No requests are made to check for newer versions
- The update warning indicator (⚠️) is not shown

This is useful for:
- Local development images that don't need update checks
- Containers with private registries that Porthole cannot query
- Reducing network requests for containers you don't want to update
