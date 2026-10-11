# Container endpoint discovery

## Purpose and agreed behavior

Porthole currently treats a published Docker port as the only way to open a
container. Services exposed through Traefik without published ports appear
disabled or are excluded entirely. Transmission demonstrates another failure:
its published torrent port becomes the launch target even though its UI is
available through a Traefik router.

Discover access candidates from both published ports and routing labels. Prefer
Traefik automatically, while letting the user explicitly select either source.
Keep discovery extensible so another proxy can be supported without changing
the API contract or embedding proxy parsing in the frontend.

The user approved these behaviors in conversation:

- Save Docker ports and labels internally; expose one endpoint list in the API.
- Prefer a saved selection over automatic selection, including a saved port.
- Prefer Traefik when there is no valid saved selection.
- For multiple Traefik candidates, use the root URL if exactly one exists;
  otherwise open the settings chooser.
- Show separate Traefik URLs and Published Ports sections in container settings,
  followed by the existing Version Updates section.

## Architecture and data flow

The existing flow maps docker-java `Container` objects directly into
`ContainerDTO`. Introduce a small internal `ContainerSnapshot` containing the
existing container metadata, `publishedPorts: Set<Integer>`, and
`labels: Map<String, String>`. Null Docker collections become empty collections.
Take immutable copies; raw labels remain internal and are not serialized by the
containers API. No database or durable backend storage is introduced.

Use an `EndpointProvider` interface with a single operation:

```java
List<EndpointDTO> discover(ContainerSnapshot container);
```

Initial providers are `PublishedPortEndpointProvider` and
`TraefikEndpointProvider`. An `EndpointDiscovery` component takes the Spring
list of providers and aggregates their results. `ContainerMapper` maps Docker
data into the snapshot, invokes discovery, and builds the API DTO. This retains
the current service/controller structure while separating provider logic.

Adding a proxy requires a provider returning the same endpoint contract. A
provider can receive its own dependencies or configuration through Spring.
The provider interface makes no requirement that discovery must use labels.
This version implements Traefik Docker labels only.

## API contract

Replace `ContainerDTO.exposedPorts` with `endpoints: List<EndpointDTO>` and
replace `hasPublicPorts` with `hasEndpoints`. Keep other container fields.

Every endpoint contains `id`, `source`, `url`, and `port`:

```json
{
  "endpoints": [
    {
      "id": "traefik:https://transmission.example.com/",
      "source": "traefik",
      "url": "https://transmission.example.com/",
      "port": null
    },
    {
      "id": "published-port:51413",
      "source": "published-port",
      "url": null,
      "port": 51413
    }
  ],
  "hasEndpoints": true
}
```

For URL endpoints, `url` is an absolute HTTP(S) URL and `port` is null. For
published-port endpoints, `url` is null and `port` is a valid Docker public port.
The frontend builds their target using the existing
`http://${window.location.hostname}:${port}` behavior. The API never substitutes
the backend container hostname or Docker daemon address for the browser host.

`source` is an extensible string identifier, not a closed list of proxy names.
IDs combine source and normalized destination, so selections survive container
recreation and router renaming. URL normalization lowercases the hostname,
normalizes an empty path to `/`, and omits default HTTP(S) ports while preserving
path case and meaningful trailing slashes. Deduplicate by source and destination;
do not discard a port candidate because a proxy candidate might reach the same
service. Sort deterministically by source and destination; ordering does not
select the default.

Rename the `/api/containers` query parameter `includeWithoutPorts` to
`includeWithoutEndpoints`; its default remains false. Filter after discovery so
label-only containers appear by default. `includeStopped` retains its behavior.
This is a deliberate API change shipped with the bundled client: no legacy DTO
fields or query-parameter alias. Update API documentation and callers together.

## Published-port discovery

Retain the current public-port extraction and numeric deduplication. Unpublished
container ports produce no candidates. This version preserves the existing
protocol-agnostic behavior rather than changing which public ports are listed.
A candidate does not establish that the destination is a web UI; there is no
HTTP probe, port allowlist, or protocol detection in this feature.

## Traefik discovery

Read labels from Docker's container listing, where Compose substitutions have
already been resolved. Do not read Compose files or environment files at runtime.
Match label keys case-insensitively and keep their values unchanged. Discover
every explicitly labeled `traefik.http.routers.<name>.rule`. Skip Traefik discovery
when `traefik.enable=false`; explicit router labels are sufficient when the enable
label is absent. TCP and UDP routers do not produce browser URLs.

The supported rule grammar is one literal `Host` matcher, optionally joined by
`&&` to one literal `Path` or `PathPrefix` matcher. Each matcher has one argument;
backtick and double-quoted literals and grouping parentheses are supported.
The parser must consume the entire expression. Unsupported expressions, invalid
hosts/paths, unresolved substitutions, OR, negation, regex matchers, multiple
arguments, and other predicates produce no endpoint for that router. Do not
extract a host substring and silently discard the rest of a rule.

Host-only rules produce a root URL. Host plus Path/PathPrefix rules produce the
literal path as the candidate destination. This includes ShelfPort's `/dl/`
router even though that path is not the main UI. Deduplicate identical URLs
from multiple routers, and preserve distinct hosts, paths, and schemes.

Infer HTTPS from explicit router `tls=true`, or a router TLS sublabel such as
`tls.certresolver` when `tls` is not explicitly false. Otherwise infer HTTP.
Entrypoint names such as `websecure` are not authoritative scheme or port data.
Assume standard external ports 80/443; backend
`loadbalancer.server.port` and `loadbalancer.server.scheme` must not become the
browser-facing port or scheme.

These are label-derived candidates, not validated effective Traefik routes.
Global entrypoint TLS defaults, custom external ports, default router rules,
middleware redirects/rewrites, provider constraints, and file-defined routes
are outside this version. Parsing one unsupported or malformed router must not
fail discovery for other routers, providers, or containers. Skip that router and
emit a debug diagnostic identifying the container/router without dumping labels.

## Automatic selection and saved preferences

On a tile click:

1. Open the saved endpoint if its ID still exists.
2. Otherwise consider the Traefik candidates. Open the only Traefik candidate
   if there is one. If several exist and exactly one has path `/`, open that
   root URL. If several exist without a unique root, open settings.
3. If there are no Traefik candidates, open the only remaining candidate or
   open settings when several exist.
4. If there are no candidates, keep the tile disabled; settings remain usable.

Automatic selection is computed, not persisted. Settings show a checkmark on
the effective selection when there is one. Choosing any option persists its ID
and overrides automatic preference. An unavailable saved ID is ignored for
selection; never open a destination solely from stale local storage.

Store selection with a new per-container-name local-storage key. Migrate an
existing numeric `port_pref_<name>` to the corresponding published-port ID when
that port still exists and there is no new saved preference. Preserve the old
key; the new key takes precedence. This preserves existing explicit port choices.
Keep the current container-name preference scope and cross-tab synchronization.

## Settings and frontend behavior

Show Traefik URLs first, Published Ports second, and Version Updates last. Omit
empty source sections. Display full URLs for Traefik candidates and `:<port>` for
port candidates. Long URLs wrap within the modal. Use keyboard-operable controls
and expose the single selected option accessibly across all source groups.

Group by `source` with a centralized source-to-title mapping. Future sources get
a generic readable title until given a friendly display name; their HTTP(S)
URL candidates use the same selection and launch logic. No Traefik label parsing
occurs in React. Only Traefik receives automatic source preference in this version.

Replace port-only logic throughout tile enablement, settings props, API requests,
local-storage selection, and memo comparison. Compare endpoint contents so a
refreshed URL changes the tile even when its container ID remains the same.
Retain the current Show All setting, now including containers without endpoints.

## Verification and acceptance criteria

Unit coverage must verify published ports, empty labels, multiple routers,
TLS inference, host/path parsing, deduplication, stable IDs, disabled Traefik,
and unsupported rules without accidental partial extraction. Mapper/service
tests must verify that label-only containers survive the default filter and that
raw labels and old port fields do not leak into the API.

Frontend coverage must verify saved choices over Traefik defaults, sole Traefik
selection despite published ports, unique-root preference, ambiguous routes
opening settings, port fallback, stale preferences, legacy port migration,
source grouping, checkmarks, and changes after a container refresh.

Add Docker-backed integration fixtures with labels and no published ports, plus
a container with both labels and a published port. Verify the serialized API
contract and filtering with `includeWithoutEndpoints`. No live Traefik instance
is required because this feature interprets Docker metadata rather than querying
Traefik or validating reachability.

During implementation, run server unit tests, client tests/lint/build, and
Spotless checks. Rebuild the JVM image before server ITs when server source
changes. Register reflection binding for endpoint DTOs; regenerate native hints
when reflection-dependent types are added, without editing generated JSON by
hand. Verify the same IT suite against the native image to check AOT serialization.
Update Architecture and Configuration documentation for the new contract and
selection behavior. No runtime tests are required for this design-only commit.

## References

- [Traefik Docker routing labels](https://doc.traefik.io/traefik/reference/routing-configuration/other-providers/docker/)
- [Traefik HTTP TLS configuration](https://doc.traefik.io/traefik/reference/routing-configuration/http/tls/overview/)
- [Traefik entrypoints](https://doc.traefik.io/traefik/routing/entrypoints/)
- Local motivating example: `services/smh/compose.yml`, including its host-only
  routers, Transmission's published torrent ports, and ShelfPort's `/dl/` router.
