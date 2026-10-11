# Container Endpoint Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Discover complete service URLs from Docker published ports and routing labels, preferring Traefik while preserving explicit user choices.

**Architecture:** Spring injects every `EndpointProvider` bean into an aggregator. Docker metadata stays in an internal snapshot; the API exposes `{id, source, url}` candidates. React groups all sources dynamically and persists endpoint selections in local storage.

**Tech Stack:** Java 25, Spring Boot 4, docker-java, JSpecify, GraalVM, React 19, Vite, React Query, Vitest, JUnit, Mockito, and Testcontainers; use existing dependencies.

**Spec:** [Approved endpoint discovery design](../specs/2026-10-11-endpoint-discovery-design.md). Read the spec, repository guidance, and Architecture/Development/Configuration guides before executing.

## Global Constraints

- Keep one Spring Boot application with its bundled React client and native executable.
- Every endpoint contains `id`, `source`, and a non-null absolute HTTP(S) `url`.
- No separate endpoint `port` field, client URL construction, raw-label serialization, or manually maintained provider registry.
- Spring supplies `List<EndpointProvider>` by constructor injection; a new implementation bean works without changing consumers.
- `source` is an extensible string identifier, not a closed list of proxy names.
- Saved selection wins; otherwise prefer the only Traefik URL or unique Traefik root URL. Ambiguity opens settings; without Traefik, use a sole candidate or open settings.
- Published-port IDs remain `published-port:<port>` regardless of request hostname; Traefik IDs use `traefik:<normalized-url>`.
- Direct-port links use HTTP and the optional `porthole.endpoints.published-host` override, then request server name. The environment variable is `PORTHOLE_ENDPOINTS_PUBLISHEDHOST`.
- Support literal Host, optionally conjoined with one literal Path/PathPrefix; fully consume rules and skip unsupported expressions. Preserve the spec's TLS and standard-port limitations.
- Use the documented package layers, JSpecify/`@NullMarked`, immutable collections, validated configuration properties, and native binding hints.
- Required Docker-backed component tests live under `server/src/test/java/com/roomelephant/porthole/it`; unit tests do not substitute for them. Run both JVM and native images.
- Apply server formatting before commits; use per-component Makefiles and preserve existing coverage gates.
- Only commit task-owned files. Leave the existing modified `CLAUDE.md` and untracked `AGENTS.md` untouched.
- This plan is on a docs branch. Before implementing, use a `feature/endpoint-discovery` branch; any PR must carry the `feature` label.

## Review Focus

- Mixed-case label keys, quoted literals, and trailing rule predicates must not silently create incorrect URLs; pinned in Task 2.
- Hostname changes and IPv6 must produce correct per-request URLs without singleton-state leakage; pinned in Tasks 1, 3, and 5.
- Corrupt/stale storage, unavailable legacy ports, and storage write failures must preserve usable automatic selection; pinned in Task 4.
- Same container/endpoint IDs with refreshed URLs and cross-tab preferences must update subsequent launches; pinned in Task 4.
- Unknown source identifiers, including `__proto__`, and very long URLs must remain selectable in settings; pinned in Task 4.

## File structure

All server paths below are relative to `server/src/main/java/com/roomelephant/porthole/` unless prefixed with `server/src/test/`.

| Files | Responsibility |
|---|---|
| `domain/model/{ContainerSnapshot,EndpointContext,EndpointDTO}.java` | Immutable discovery input, request context, and API endpoint value |
| `domain/component/endpoint/{EndpointProvider,EndpointDiscovery,PublishedPortEndpointProvider}.java` | Shared contract, automatic aggregation, and direct-port candidates |
| `domain/component/endpoint/{TraefikRuleParser,TraefikEndpointProvider}.java` | Restricted rule parsing and Traefik URL discovery |
| `domain/util/EndpointUrls.java` | URI construction/normalization shared by providers |
| `config/properties/EndpointProperties.java` | Optional validated hostname override, scanned by existing application configuration |
| `controller/EndpointContextResolver.java` | Resolve hostname from configuration and servlet request at the HTTP boundary |
| Existing DTO, mapper, service, controller | Replace port API and propagate immutable request context |
| `client/src/utils/endpoints.js` | Pure selection policy and dynamic source grouping |
| `client/src/hooks/useEndpointSelection.js` | Stored selection, migration, and cross-tab behavior using existing storage hook |
| Existing React components/API/constants/CSS | Show source groups, open returned URLs, and rename port-only copy |
| Existing `it/controller` and `it/infra` tests | Required Docker-backed API scenarios and explicit runtime-image selection |

Tasks are sequential because the API and client depend on the shared endpoint contract. Do not release or open a finished feature PR until all tasks pass.

### Task 1: Shared discovery contract and published-port URLs

**Files:** Create the three model files, `EndpointProvider`, `EndpointDiscovery`, `PublishedPortEndpointProvider`, `EndpointUrls`, `EndpointProperties`, and `EndpointContextResolver` listed above. Create corresponding tests under `server/src/test/java/com/roomelephant/porthole/domain/component/endpoint/`, `domain/util/`, `config/properties/`, and `controller/`. Existing product flow stays unchanged until Task 3.

**Interfaces:**

- `EndpointDTO(String id, String source, String url)` with native binding registration.
- `EndpointContext(String publishedHost)`; hostname is non-null.
- `ContainerSnapshot(String id, String name, String image, Set<Integer> publishedPorts, Map<String,String> labels, String state, String status)` takes immutable collection copies; display name, Compose project, and icon are computed by the existing mapper. Preserve existing metadata nullability with explicit JSpecify annotations where needed.
- `EndpointProvider.discover(ContainerSnapshot container, EndpointContext context) -> List<EndpointDTO>`.
- `EndpointDiscovery(List<EndpointProvider> providers)`; `discover(ContainerSnapshot, EndpointContext) -> List<EndpointDTO>` deduplicates IDs and sorts by source/destination.
- `EndpointUrls.publishedPortUrl(String host, int port) -> String` and `routeUrl(String scheme, String host, String path) -> String` construct normalized URIs.
- `EndpointProperties(@Nullable String publishedHost)` uses prefix `porthole.endpoints`, startup validation, and native binding registration.
- `EndpointContextResolver(EndpointProperties properties)`; `resolve(HttpServletRequest request) -> EndpointContext` reads configured hostname first, otherwise `request.getServerName()`.

- [ ] **Step 1: Write failing provider, URI, context, and Spring-wiring tests.** Name and pin these cases:
  - `shouldBuildCompletePublishedPortUrl`: port `51413` and host `192.168.50.6` yield exactly `EndpointDTO("published-port:51413", "published-port", "http://192.168.50.6:51413/")`.
  - `shouldPreservePortIdAcrossHostnames`: changing host changes URL but not ID.
  - `shouldFormatIpv6Url`: `2001:db8::6` and `51413` yield `http://[2001:db8::6]:51413/`.
  - `shouldResolveOverrideBeforeRequestHost`: configured `docker.example.com` wins over `proxy.example.com`; absent configuration uses request server name, ignoring request scheme/port.
  - `shouldRejectConfiguredUrlInsteadOfHostname`: reject `http://192.168.50.6`, `host:9753`, and host values with a path; accept hostname, IPv4, and IPv6 literals.
  - `shouldBindPublishedHostEnvironmentVariable`: a Spring environment property source containing `PORTHOLE_ENDPOINTS_PUBLISHEDHOST=192.168.50.6` binds the configuration record's `publishedHost` to `192.168.50.6`.
  - `shouldNormalizeRouteUrl`: scheme `https`, host `EXAMPLE.COM`, and empty path yield `https://example.com/`; `/DL/` preserves case and trailing slash.
  - `shouldInjectEveryProviderBean`: a minimal Spring context with discovery, the port provider, and a test-only provider returning source `ngnext` aggregates both without production registration changes.
  - `shouldDeduplicateAndSortWithoutSelectingDefault`: duplicate IDs collapse, unrelated sources remain, and a provider returning an empty list does not remove another's results.

- [ ] **Step 2: Run the new test classes before implementation.** Use `make -C server test-one TEST=PublishedPortEndpointProviderTest,EndpointUrlsTest,EndpointContextResolverTest,EndpointPropertiesTest,EndpointDiscoveryTest`. Expect missing types or failing assertions. Do not count dependency/environment errors as the intended failure.

- [ ] **Step 3: Implement the contracts and port discovery.** Use Spring beans and constructor injection; keep request state only in `EndpointContext`. Use a URI builder rather than concatenating IPv6 addresses. Keep `publishedPorts` numeric/protocol-agnostic as specified; inputs with no ports return an empty list. Keep servlet dependencies in the resolver/controller package.

- [ ] **Step 4: Run the same tests and server unit suite.** Require all new tests and `make -C server test` to pass; inspect the Spring-wiring assertions and configuration-validation failures.

- [ ] **Step 5: Format and commit the task-owned files.** Run `make -C server format`, check the diff, stage only listed additions, and commit `feat: add extensible endpoint discovery and port URLs`.

### Task 2: Restricted Traefik routing provider

**Files:** Create `domain/component/endpoint/TraefikRuleParser.java` and `TraefikEndpointProvider.java`; create `TraefikRuleParserTest.java` and `TraefikEndpointProviderTest.java` in the matching test package. Extend `EndpointUrlsTest` if route-normalization cases need coverage.

**Interfaces:** Consumes Task 1 models, provider contract, and URL helper. Produces `TraefikRuleParser.parse(String rule) -> Optional<Route>`, where nested `Route(String host, String path)` contains a literal host and root/literal path. `TraefikEndpointProvider(TraefikRuleParser parser)` implements `discover` and registers as a Spring bean.

- [ ] **Step 1: Write failing parser/provider tests.** Use resolved runtime labels and assert complete endpoint objects:
  - `shouldDiscoverHostOnlyTlsRouter`: `Host(\`transmission.example.com\`)` plus `tls=true` yields URL `https://transmission.example.com/` and ID `traefik:https://transmission.example.com/`.
  - `shouldKeepRootAndDownloadRoutes`: host-only and host plus `PathPrefix(\`/dl/\`)` yield both root and `/dl/`; identical URLs from renamed routers collapse.
  - `shouldParseQuotedLiteralsAndGrouping`: support double-quoted arguments and parentheses around the supported conjunction; preserve path case and trailing slash.
  - `shouldConsumeTheWholeRule`: `Host(\`example.com\`) && Method(\`POST\`)`, OR, negation, regex, multiple arguments, unresolved variables, malformed syntax, and invalid host/path return no candidate.
  - `shouldHandleMixedCaseLabelKeys`: uppercase/mixed-case Traefik keys produce the same candidates; `traefik.enable=false` disables them.
  - `shouldInferSchemeFromRouterTls`: explicit true or a TLS sublabel without explicit false gives HTTPS; explicit false or absent TLS gives HTTP. Entrypoint names and backend server scheme/port do not override this policy.
  - `shouldKeepOtherCandidatesWhenOneRouterIsInvalid`: one unsupported rule does not remove another router's endpoint.
  - `shouldIgnoreTcpUdpAndMissingRule`: TCP/UDP labels and label-free snapshots produce empty results; absence of `traefik.enable` does not suppress an explicit HTTP router.

- [ ] **Step 2: Run `make -C server test-one TEST=TraefikRuleParserTest,TraefikEndpointProviderTest,EndpointUrlsTest`.** Expect missing parser/provider types or the new behavioral assertions to fail.

- [ ] **Step 3: Implement the restricted grammar and provider.** Consume the entire expression, including grouping and whitespace; do not use a host-substring extraction regex that ignores predicates. Normalize label keys case-insensitively, preserve values, and build URLs through Task 1's helper. Debug diagnostics identify the container/router without dumping labels. Use no Traefik API connection, probes, or external parser dependency.

- [ ] **Step 4: Run the same targeted tests and `make -C server test`.** Require successful parsing of supported forms and empty results for every unsupported form.

- [ ] **Step 5: Format, inspect, and commit only this task's files.** Commit `feat: discover service URLs from Traefik routers`.

### Task 3: Replace the container API with endpoints

**Files:** Modify `domain/model/ContainerDTO.java`, `domain/mapper/ContainerMapper.java`, `domain/service/ContainerService.java`, and `controller/ContainerController.java`. Update matching `ContainerDTOTest`, `ContainerMapperTest`, `ContainerServiceTest`, `ContainerControllerTest`, and existing accessor/fixture usages in `it/controller/ContainersEndpointIT.java` so all test sources compile with the new record.

**Interfaces:**

- `ContainerDTO` replaces `Set<Integer> exposedPorts` with `List<EndpointDTO> endpoints`; `hasEndpoints()` is serialized as `hasEndpoints`.
- `ContainerMapper(IconComponent, EndpointDiscovery)`; `toDTO(Container container, EndpointContext context) -> ContainerDTO` extracts the immutable snapshot and invokes discovery.
- `ContainerService.getContainers(boolean includeWithoutEndpoints, boolean includeStopped, EndpointContext context) -> List<ContainerDTO>` filters after discovery.
- Controller uses `EndpointContextResolver` and accepts `includeWithoutEndpoints=false` and `includeStopped=false`; version endpoint and Docker error handling remain intact.

- [ ] **Step 1: Write/update failing mapper, service, DTO, and MockMvc tests.** Assertions must pin:
  - Runtime Docker labels reach the providers; null labels/port arrays become empty collections, and null public-port values are ignored.
  - A label-only running container survives default endpoint filtering.
  - Ports and labels from the same container return both endpoint sources; project/display-name/icon behavior remains as before.
  - MockMvc requests with server names `192.168.50.6` and `other.example.com` propagate separate contexts through the same singleton application beans.
  - JSON includes `endpoints[*].id/source/url` and `hasEndpoints`, but omits `exposedPorts`, `hasPublicPorts`, labels, and endpoint `port`.
  - `includeWithoutEndpoints=true` passes the renamed flag; the old query parameter is not an alias. `includeStopped` and Docker-connection exception behavior retain existing assertions.

- [ ] **Step 2: Run `make -C server test-one TEST=ContainerDTOTest,ContainerMapperTest,ContainerServiceTest,ContainerControllerTest`.** Expect missing signatures/record accessors or changed-contract assertions to fail.

- [ ] **Step 3: Implement the API migration and context propagation.** Preserve unchanged metadata behavior, normalize absent collections, copy labels/ports, and keep raw labels outside the API DTO. Register endpoint serialization binding. Migrate existing IT assertions to endpoint IDs/URLs so compilation succeeds; new Docker-backed scenarios belong to Task 5.

- [ ] **Step 4: Run the targeted tests and `make -C server test`.** Require all tests to pass and confirm both hostnames produce different URLs while port IDs stay stable.

- [ ] **Step 5: Format, inspect, and commit only this task's files.** Commit `feat: expose discovered endpoints through the containers API`. The feature branch is temporarily incompatible with the old client until Task 4; do not publish it at this point.

### Task 4: URL selection, saved preferences, and grouped settings

**Files:** Create `client/src/utils/endpoints.js`, `client/src/hooks/useEndpointSelection.js`, and matching tests in `client/src/test/utils/` and `client/src/test/hooks/`. Modify `client/src/components/{ContainerTile,ContainerSettings,AppSettings}.jsx`, `client/src/{App.jsx,api.js,constants.js,index.css}`, and `client/src/utils/containers.js`. Update existing affected component/API/constants/utility tests and `client/src/test/testUtils.jsx` to the new endpoint fixtures.

**Interfaces:**

- `resolveEndpointSelection(endpoints, savedId) -> Endpoint | null` implements the spec's launch policy without mutating storage.
- `groupEndpointsBySource(endpoints) -> Array<{source, title, endpoints}>` groups every source; Traefik first, Published Ports second, other source identifiers in deterministic order.
- `useEndpointSelection(containerName, endpoints) -> {selectedEndpoint, selectEndpoint}`; `selectEndpoint(id)` stores an explicit endpoint ID.
- `STORAGE_KEYS.ENDPOINT_PREF(name)` returns `endpoint_pref_${name}`; retain `PORT_PREF(name)` only to read legacy `port_pref_${name}` preferences.
- `ContainerSettings` consumes `endpoints`, `selectedEndpointId`, and `onSelectEndpoint(id)` instead of port props. Version-update props are preserved.
- Tile launches `selectedEndpoint.url` directly or opens settings; memo comparison includes every endpoint's ID, source, and URL.

- [ ] **Step 1: Write failing pure-selection tests.** Pin saved port over Traefik, sole Traefik over any ports, unique root over `/dl/`, two root URLs opening settings, multiple non-root URLs opening settings, no-Traefik single/multiple candidate behavior, unknown-source candidates, no endpoints, and stale IDs falling back to automatic selection.

- [ ] **Step 2: Run `npm --prefix client run test:run -- src/test/utils/endpoints.test.js`.** Expect a missing utility or policy assertion failure.

- [ ] **Step 3: Implement the pure selection and source grouping utilities.** Return null for ambiguity/no candidate. Use Map or an equivalent grouping structure safe for identifiers such as `__proto__`; derive readable titles for unfamiliar sources without an allowlist.

- [ ] **Step 4: Write failing hook and component tests.** Cover:
  - Valid legacy numeric port migration to its matching `published-port:<port>` ID only when the new key is absent; preserve the old key.
  - A new preference always wins over legacy storage; malformed/non-numeric legacy values and removed ports are ignored.
  - Automatic defaults do not write storage; unavailable saved IDs do not open stale URLs.
  - Cross-tab storage events change the effective selection, and quota/write failures retain a usable in-memory selection.
  - Settings display full URLs under Traefik URLs, Published Ports, and an unfamiliar source section; selecting a radio option passes its ID. Exactly one effective choice shows a checkmark; ambiguity shows none.
  - `__proto__` source candidates remain selectable, and keyboard navigation/select controls are accessible.
  - Rerendering the same container and endpoint IDs with a changed URL causes the next tile click to open the new URL.
  - Settings-button clicks do not launch a URL; no-endpoint tiles stay disabled while settings/version controls still work.
  - The API request uses `includeWithoutEndpoints`; Show All and empty-state copy say "without endpoints".

- [ ] **Step 5: Run `npm --prefix client run test:run -- src/test/hooks/useEndpointSelection.test.jsx src/test/components/ContainerTile.test.jsx src/test/components/ContainerSettings.test.jsx src/test/api.test.js`.** Expect the new props, launch policy, or persistence assertions to fail.

- [ ] **Step 6: Implement storage migration and connect the UI.** Reuse `useLocalStorage` for cross-tab synchronization; persist IDs only after explicit selection or valid legacy migration. Render accessible single-selection controls across all source groups, with the existing version section last. Add URL wrapping using `overflow-wrap:anywhere` and `min-width:0` where needed. Remove `getTargetUrl`; the client never consults its hostname to launch endpoints. Update all test fixtures and port-only copy together.

- [ ] **Step 7: Run the new and affected tests, then `make -C client test`, `make -C client lint`, and `make -C client build`.** Require passing coverage gates, lint, and build. Visually inspect a modal with root/path routes, a published-port URL, an unfamiliar source, and a long URL at desktop and narrow viewport widths.

- [ ] **Step 8: Inspect and commit only this task's files.** Commit `feat: prefer Traefik URLs and group endpoint settings`.

### Task 5: Required Docker-backed component tests on JVM and native

**Files:** Extend `server/src/test/java/com/roomelephant/porthole/it/controller/ContainersEndpointIT.java` and fixtures in `it/infra/ContainerAwareIntegrationTestBase.java`. Modify `it/infra/PortholeContainer.java` to permit selecting the runtime image for verification. If override tests need a separate app lifecycle, create `it/controller/PublishedHostOverrideIT.java` using the existing infrastructure rather than a new framework.

**Interfaces:** Default IT image remains `porthole-jvm:latest`. Resolve `System.getProperty("porthole.it.image", "porthole-jvm:latest")` for both normal and custom-socket app factories. A command-line `-Dporthole.it.image=<tag>` selects the explicit native image. Use `PORTHOLE_ENDPOINTS_PUBLISHEDHOST` on a dedicated app instance for override scenarios. Shared component tests still consume the same `/api/containers` contract from Task 3.

- [ ] **Step 1: Write the required failing component scenarios.** Use real Docker containers with resolved labels:
  - `shouldIncludeTraefikContainerWithoutPublishedPorts`: label-only container appears by default with the exact HTTPS URL.
  - `shouldReturnProxyAndPublishedPortCandidates`: mixed container exposes both sources and its mapped port in the complete HTTP URL.
  - `shouldKeepDistinctRoutesAndDeduplicateEquivalentRouters`: root and `/dl/` persist, duplicate root routers collapse.
  - `shouldSkipDisabledAndUnsupportedRoutersWithoutLosingOtherCandidates`: disabled/invalid routes do not affect valid ports or other routers.
  - `shouldFilterContainersWithoutEndpointsAndRespectStoppedFlag`: pin default and both query flags independently, retaining existing stopped-container coverage.
  - `shouldUseConfiguredPublishedHost`: dedicated app configured with `docker.example.com` returns that hostname despite a request through localhost.
  - `shouldSerializeOnlyEndpointContract`: inspect actual JSON, not only deserialized DTOs, and assert all mandatory fields plus absence of labels/legacy port fields.

- [ ] **Step 2: Run the new component scenarios.** Run `make bundle`, `make -C docker jvm-image`, and `make -C server it` against the current application. Missing new fixtures or override setup should fail the corresponding assertions before Step 3 supplies them. If a scenario already passes because Tasks 1–4 implemented its behavior, record that result; do not manufacture a failure. The production behavior's red/green cycle belongs to its owning earlier task. Do not count stale images or Docker-environment errors as regression evidence.

- [ ] **Step 3: Implement fixture lifecycle and explicit image selection.** Add labels and mappings using the existing pause-image fixtures. Ensure new fixture containers are cleaned up. Keep override configuration isolated to its dedicated Porthole instance. Do not query Traefik or perform web reachability probes. This fixture change enables native verification; fixing the existing release workflow's image-tag mismatch remains a separate tooling change.

- [ ] **Step 4: Build current artifacts and run the JVM component suite.** Run `make bundle`, `make -C docker jvm-image`, then `make -C server it`. Require all failsafe tests to pass, including the new component scenarios and existing health/resilience/version tests. Verify the launched app image is `porthole-jvm:latest`.

- [ ] **Step 5: Verify native serialization with the same suite.** Regenerate native hints with `make server-native-hints` when required; inspect generated changes and never edit the generated metadata manually. In a Linux environment with GraalVM JDK 25, run `make bundle-native` using the same prerequisites as the release workflow. A Linux CI artifact is usable only if built from this feature branch's tested commit. The macOS executable from a local native build cannot run in the Debian image. Stage the Linux binary as `porthole` in a temporary Docker build context containing the repository's production Dockerfile, entrypoint, and templates. Run `docker build -t porthole-native-validation:latest -f docker/Dockerfile <temporary-context>`, then `make -C server it MVN='mvn -Dporthole.it.image=porthole-native-validation:latest'`. Require the same tests to pass; record image identity and architecture. Do not treat JVM success as native evidence or overwrite a tracked repository file with the staged binary.

- [ ] **Step 6: Format and commit this task's tests plus generated hints when needed.** Commit `test: cover endpoint discovery through packaged application`. If the runtime environment prevents native compilation/execution, report that limitation and keep native verification outstanding.

### Task 6: Documentation, complete verification, and branch review

**Files:** Update `README.md`, `docs/ARCHITECTURE.md`, and `docs/CONFIGURATION.md`. Update `server/README.md` with the new IT image override command. Keep implementation notes out of end-user settings copy.

**Interfaces:** Consumes the final API, provider contract, frontend behavior, and component-test commands from Tasks 1–5. Produces documented behavior and a branch ready for integration review.

- [ ] **Step 1: Document the shipped behavior.** Explain endpoint discovery and selection, Traefik/port sections, label-only inclusion, the renamed query flag, and the hostname override/default. Add a provider extension example using `@Component implements EndpointProvider` with no manual registry. Describe literal rule support and global Traefik/custom-port limitations. Show the exact `{id,source,url}` schema and `PORTHOLE_ENDPOINTS_PUBLISHEDHOST` name.

- [ ] **Step 2: Check for missed port-contract consumers.** Run `rg -n 'exposedPorts|hasPublicPorts|includeWithoutPorts|getTargetUrl|selectedPort' server/src client/src README.md docs/ARCHITECTURE.md docs/CONFIGURATION.md`. Remaining occurrences must be legacy-absence assertions or documented migration notes, not live consumers. Confirm the implementation matches every spec acceptance criterion, including component tests.

- [ ] **Step 3: Verify the assembled branch.** Run `make -C server format`, `make -C server format-check`, `make -C server test`, `make -C client test`, `make -C client lint`, and `make -C client build`. Run `mvn -f server/pom.xml verify -Dspotless.check.skip=true` to exercise the existing server coverage gates. If any runtime-affecting change occurred after Task 5, repackage/rebuild and rerun the affected JVM/native component suite; otherwise retain Task 5's recorded image/test evidence. Run `git diff --check`. Require all applicable checks to pass.

- [ ] **Step 4: Commit documentation and request whole-branch review.** Commit `docs: explain endpoint discovery and provider extensions`. Review the full diff against the approved spec and existing architecture before integration; do not review only the last commit. Report what changed, test results, native runtime evidence, and any remaining limitation. Use `feature` as the PR label if a PR is authorized.

## Execution handoff

Implementation begins after the user reviews this plan and selects an execution
method. Recommended: implement in this session using `superpowers:executing-plans`
because the six tasks share DTO/provider interfaces and ordered API/client
migration. A subagent-driven approach is also available for implementation with
independent task reviews. Do not spawn implementers before that choice.
