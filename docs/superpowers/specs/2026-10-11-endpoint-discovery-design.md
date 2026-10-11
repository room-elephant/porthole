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
- Every API endpoint provides a complete URL, including published-port candidates.
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

Use one shared `EndpointProvider` interface with a single operation:

```java
public interface EndpointProvider {
    List<EndpointDTO> discover(ContainerSnapshot container, EndpointContext context);
}
```

Initial providers are `PublishedPortEndpointProvider` and
`TraefikEndpointProvider`. Each implements this same interface and registers as
a Spring bean, normally using `@Component`. `EndpointDiscovery` receives
`List<EndpointProvider>` through constructor injection. Spring supplies every
implementation bean; discovery invokes all of them and aggregates their results.
There is no manually maintained provider list, factory, enum, or provider-name
switch in discovery. A provider returns an empty list when the container has no
applicable candidates. It returns complete URLs with its source identifier and
stable endpoint IDs when candidates exist.

`ContainerMapper` maps Docker data into the snapshot, invokes discovery, and
builds the API DTO. The controller resolves an immutable `EndpointContext`
containing the published-port hostname
once per request and passes it through the service and mapper into discovery.
Keep servlet request types at the controller boundary. This retains the current
service/controller structure while separating provider logic.

Adding another proxy means implementing `EndpointProvider` in a new class and
registering that class as a Spring bean within component scanning (or through
`@Bean`). For example, an `NgNextEndpointProvider` would implement the same
interface as Traefik, rather than introducing a new interface. Its endpoints then
appear automatically in API responses and settings, with no changes to
`EndpointDiscovery`, the mapper, service, controller, or frontend source registry.
Any provider-specific dependencies or configuration are injected by Spring.
The interface does not require providers to use labels. This version implements
the published-port and Traefik providers; other proxies are extension examples.

### Fit with the existing architecture

Keep the application as one Spring Boot backend with its bundled React client
and GraalVM native executable. Use existing package responsibilities:

- `domain/model`: snapshot, endpoint DTO, and immutable endpoint context.
- `domain/component/endpoint`: provider interface, implementations, and discovery
  component, within the existing domain component layer.
- `domain/mapper`: Docker-to-model/API mapping in `ContainerMapper`.
- `domain/service` and `controller`: retain container listing, error handling,
  filtering, and HTTP request handling in their current layers.
- `config/properties`: a validated `EndpointProperties` record with prefix
  `porthole.endpoints`, discovered by the existing `@ConfigurationPropertiesScan`.

Follow the repository's JSpecify/`@NullMarked` conventions: provider results,
collection inputs, context hostname, and endpoint fields are non-null; only the
optional configured hostname is nullable. Register reflection binding for new
API DTOs and configuration properties, as existing configuration records do.
Use Spring constructor injection and existing dependencies. Retain React Query
for server state and browser local storage for selections. No new deployment,
storage system, client state framework, or Docker transport is introduced.

## API contract

Replace `ContainerDTO.exposedPorts` with `endpoints: List<EndpointDTO>` and
replace `hasPublicPorts` with `hasEndpoints`. Keep other container fields.

Every endpoint contains `id`, `source`, and a non-null absolute HTTP(S) `url`.
There is no separate `port` field and no client-side URL construction:

```json
{
  "endpoints": [
    {
      "id": "traefik:https://transmission.example.com/",
      "source": "traefik",
      "url": "https://transmission.example.com/"
    },
    {
      "id": "published-port:51413",
      "source": "published-port",
      "url": "http://192.168.50.6:51413/"
    }
  ],
  "hasEndpoints": true
}
```

The frontend opens the returned URL for every source. Published ports and raw
labels remain internal discovery inputs; the API only exposes the resulting
candidates. The example assumes Porthole was requested through
`http://192.168.50.6:9753/` and the listed containers share that Docker host.

`source` is an extensible string identifier, not a closed list of proxy names.
Traefik IDs combine source and normalized URL, so selections survive container
recreation and router renaming. Published-port IDs use `published-port:<port>`
independently of the resolved hostname, preserving preferences when Porthole is
accessed using another hostname. URL normalization lowercases the hostname,
normalizes an empty path to `/`, and omits default HTTP(S) ports while preserving
path case and meaningful trailing slashes. Deduplicate by endpoint ID;
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
container ports produce no candidates. For each public port, return
`http://<resolved-hostname>:<public-port>/`, using a URI builder that correctly
handles DNS names, IPv4, and IPv6 literals. Do not inherit Porthole's request port,
HTTPS scheme, or path: the public port replaces the port, and direct-port links
retain the existing HTTP assumption. This version preserves the existing
protocol-agnostic behavior rather than changing which public ports are listed.
A candidate does not establish that the destination is a web UI; there is no
HTTP probe, port allowlist, or protocol detection in this feature.

Resolve the published-port hostname in this order:

1. The optional `porthole.endpoints.published-host` configuration property
   (`PORTHOLE_ENDPOINTS_PUBLISHEDHOST`), containing a hostname or IP address
   without scheme, port, or path. Validate configured values at startup.
2. The incoming request's server name, using the servlet API's parsed hostname.
   For a request to `http://192.168.50.6:9753/api/containers`, this is
   `192.168.50.6`. This matches the current frontend behavior for same-origin
   API requests without requiring configuration.

Do not use the client's remote IP, the server socket's local address, the
container's network interface IP, its Docker gateway, or the Docker API connection
address. An internal container IP does not identify the LAN address on which
Docker publishes ports. Being on the same Docker host makes the direct-access
assumption useful, but does not guarantee that every published port is reachable
on that address; host-specific bindings retain the current discovery limitation.

Use the server name as exposed by the application's request handling; do not
manually read forwarded headers or enable additional header trust in this
feature. Behind a reverse proxy, use the published-host override if the request
hostname points at the proxy or differs from the Docker host. Existing correctly
configured forwarding support may provide the original server name, but does
not establish that the proxy hostname exposes Docker's direct ports. The override
also covers a remote Docker daemon or development API requests with another host.

Resolve context on each request; do not retain it in singleton provider state or
cache final endpoint URLs across request hostnames. Traefik candidates are
independent of this context and continue to use their router hostnames.

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
empty source sections. Display full returned URLs in both sections, including
published-port URLs. Long URLs wrap within the modal. Use keyboard-operable
controls and expose the single selected option accessibly across all source groups.

Group all endpoints dynamically by `source`, without a source allowlist. Use
friendly titles for the initial sources (Traefik URLs and Published Ports) and
derive a readable title from the identifier for any other source. A new provider
must get a visible, selectable section without adding a frontend mapping entry.
Its HTTP(S) URL candidates use the same selection and launch logic. No proxy
label parsing occurs in React. Only Traefik receives automatic source preference
in this version; registration of another provider does not change that policy.

Replace port-only logic throughout tile enablement, settings props, API requests,
local-storage selection, and memo comparison. Remove the frontend port-to-URL
helper. Compare endpoint contents so a refreshed URL changes the tile even when
its container ID remains the same.
Retain the current Show All setting, now including containers without endpoints.
Update its visible label and the app's empty-state messages from "without ports"
to "without endpoints" so label-only containers are described correctly.

## Verification and acceptance criteria

Unit coverage must verify published ports, empty labels, multiple routers,
TLS inference, host/path parsing, deduplication, stable IDs, disabled Traefik,
and unsupported rules without accidental partial extraction. Mapper/service
tests must verify that label-only containers survive the default filter and that
raw labels and old port fields do not leak into the API. Verify published-port
URL construction from the request hostname, override precedence, configuration
validation, IPv6 formatting, HTTP scheme preservation, and isolation between
requests using different hostnames. Stable port IDs must not depend on hostname.
Add a Spring context test with an additional test-only `EndpointProvider` bean
and verify that its endpoints appear through discovery without changing a
registry or production wiring. Verify that providers returning no candidates do
not prevent other providers from contributing endpoints.

Frontend coverage must verify saved choices over Traefik defaults, sole Traefik
selection despite published ports, unique-root preference, ambiguous routes
opening settings, port fallback, stale preferences, legacy port migration,
source grouping, checkmarks, and changes after a container refresh. All launch
tests must use the API's URL, without consulting `window.location.hostname` or
reconstructing a port URL. Include an unfamiliar source identifier and verify
that settings render its section and let the user select and open its URL.

### Required server component tests

Implementation must add or extend component tests under
`server/src/test/java/com/roomelephant/porthole/it`, using the repository's existing
Docker-backed `*IT` suite. These tests exercise the packaged application through
its HTTP API with real Docker container metadata. Unit tests and the Spring
provider-wiring test are additional coverage and do not replace component tests.

Extend `it/controller/ContainersEndpointIT.java` and reuse
`ContainerAwareIntegrationTestBase`, `IntegrationTestBase`, and `PortholeContainer`
for fixtures and application lifecycle. Component coverage must verify:

- A running container with Traefik labels and no published ports appears in the
  default response and exposes the expected complete HTTPS URL.
- A container with both Traefik labels and a published port returns candidates
  from both providers, including a complete port URL using the request hostname.
- Multiple supported routers retain distinct host/path URLs, including a root
  route and `/dl/`, while duplicate destinations produce one endpoint.
- Disabled Traefik and malformed or unsupported rules produce no Traefik
  candidates without losing valid published-port or other-router candidates.
- `includeWithoutEndpoints` controls containers without discovered candidates;
  `includeStopped` retains its existing behavior.
- The configured published-host override takes precedence over the request
  hostname in the serialized published-port URL.
- The actual JSON contains non-null absolute URLs, stable endpoint IDs, source
  identifiers, and correct `hasEndpoints`, with no raw labels, `exposedPorts`,
  `hasPublicPorts`, or endpoint `port` fields.

No live Traefik instance is required because this feature interprets Docker
metadata rather than querying Traefik or validating reachability. Preserve
existing coverage thresholds and run this component suite against both the JVM
and native application images. The feature is not complete until the required
component scenarios pass against the intended runtime images.

### Build and native verification

During implementation, run server unit tests, client tests/lint/build, and
Spotless checks using the per-component Makefiles. Apply server formatting before
committing. When server source changes, package the updated JAR before rebuilding
the JVM image and running server ITs; the Dockerfile consumes a pre-built JAR.
Register reflection binding for endpoint DTOs and configuration properties;
regenerate native hints when reflection-dependent types are added, without editing
generated JSON by hand. Verify the same IT suite against the native image to check
AOT serialization.
Confirm which image the IT fixture actually launches: `PortholeContainer`
currently hardcodes `porthole-jvm:latest`, while the release workflow builds its
native IT image as `porthole-it:latest`. A native-image check must explicitly make
the fixture launch the native image; the workflow's step title alone does not
establish that. Resolving the release workflow mismatch is a separate tooling
concern, not a change to endpoint discovery's architecture.
Update README, Architecture, and Configuration documentation for the new contract,
provider extension mechanism, optional hostname override, and selection behavior.
No runtime tests are required for this design-only commit.

## References

- [Repository architecture](../../ARCHITECTURE.md)
- [Development workflow](../../DEVELOPMENT.md)
- [Configuration conventions](../../CONFIGURATION.md)
- [Spring environment-variable binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables)
- [Traefik Docker routing labels](https://doc.traefik.io/traefik/reference/routing-configuration/other-providers/docker/)
- [Traefik HTTP TLS configuration](https://doc.traefik.io/traefik/reference/routing-configuration/http/tls/overview/)
- [Traefik entrypoints](https://doc.traefik.io/traefik/routing/entrypoints/)
- [Docker port publishing](https://docs.docker.com/engine/network/port-publishing/)
- Local motivating example: `services/smh/compose.yml`, including its host-only
  routers, Transmission's published torrent ports, and ShelfPort's `/dl/` router.
