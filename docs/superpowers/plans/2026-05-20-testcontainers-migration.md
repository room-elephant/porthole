# Simplify IT Infrastructure: Replace DinD with Standard Testcontainers

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Docker-in-Docker (DinD) integration test infrastructure with standard Testcontainers running on the host Docker daemon, and switch WireMock from a container to an embedded in-process server.

**Architecture:** Porthole mounts the host `/var/run/docker.sock` directly. Test containers run on the same daemon and are visible to Porthole. An embedded `WireMockServer` replaces the WireMock container — Porthole reaches it via `host.docker.internal:<port>`. Container count assertions in `ContainersEndpointIT` are replaced with name-based presence/absence checks since the host daemon will have Porthole and Ryuk containers in addition to test containers.

**Tech Stack:** Testcontainers 2.0.5, WireMock standalone 3.13.2, docker-java 3.7.0, JUnit Jupiter, Maven Failsafe

---

## File Map

| File | Action |
|------|--------|
| `src/test/.../infra/PortholeContainer.java` | Rewrite — standard `GenericContainer` subclass + `withCustomSocket` factory for resilience tests |
| `src/test/.../infra/IntegrationTestBase.java` | Rewrite — embedded `WireMockServer`, TC `GenericContainer` for running containers, raw docker-java for stopped container |
| `src/test/.../infra/DockerInfrastructure.java` | Delete |
| `src/test/.../infra/DinDContainer.java` | Delete |
| `src/test/.../controller/ContainersEndpointIT.java` | Modify — remove `hasSize()` assertions, update empty-list test |
| `src/test/.../resilience/DockerConnectionFailureIT.java` | Rewrite — embedded WireMock, host filesystem sockets |
| `src/test/.../health/DockerHealthIT.java` | Minor edit — comment out `shouldReturnDownWhenDockerIsUnreachable` |
| `.github/workflows/reusable-server.yml` | Simplify — remove DinD caching steps, add `docker build` step |

---

## Task 1: Rewrite PortholeContainer as a standard GenericContainer

**Files:**
- Modify: `server/src/test/java/com/roomelephant/porthole/it/infra/PortholeContainer.java`

- [ ] **Step 1: Replace the entire file with this content**

```java
package com.roomelephant.porthole.it.infra;

import java.util.Objects;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class PortholeContainer extends GenericContainer<PortholeContainer> {

    public static final int PORTHOLE_PORT = 9753;
    static final String IMAGE_TAG =
            Objects.requireNonNullElse(System.getenv("PORTHOLE_IT_IMAGE_TAG"), "porthole-it:latest");

    public PortholeContainer(int wireMockPort) {
        super(IMAGE_TAG);
        withExposedPorts(PORTHOLE_PORT);
        withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock", BindMode.READ_WRITE);
        withEnv("PORTHOLE_DOCKER_HOST", "unix:///var/run/docker.sock");
        applyRegistryEnv(this, wireMockPort);
        waitingFor(Wait.forHttp("/actuator/health")
                .forPort(PORTHOLE_PORT)
                .forStatusCodeMatching(status -> status >= 100));
    }

    public String baseUrl() {
        return "http://localhost:" + getMappedPort(PORTHOLE_PORT);
    }

    /**
     * Factory for resilience-test Porthole instances that connect to a custom socket path.
     * Does NOT mount /var/run/docker.sock — caller must add their own socket bind.
     */
    public static GenericContainer<?> withCustomSocket(String socketPath, int wireMockPort) {
        GenericContainer<?> container = new GenericContainer<>(IMAGE_TAG)
                .withExposedPorts(PORTHOLE_PORT)
                .withEnv("PORTHOLE_DOCKER_HOST", "unix://" + socketPath);
        applyRegistryEnv(container, wireMockPort);
        container.waitingFor(Wait.forHttp("/actuator/health")
                .forPort(PORTHOLE_PORT)
                .forStatusCodeMatching(status -> status >= 100));
        return container;
    }

    private static void applyRegistryEnv(GenericContainer<?> container, int wireMockPort) {
        String base = "http://host.docker.internal:" + wireMockPort;
        container
                .withEnv("REGISTRY_URLS_REGISTRY", base + "/v2/")
                .withEnv("REGISTRY_URLS_AUTH",
                        base + "/auth?service=registry.docker.io&scope=repository:")
                .withEnv("REGISTRY_URLS_REPOSITORIES", base + "/v2/repositories/")
                .withEnv("REGISTRY_CACHE_TTL", "1ms")
                .withEnv("REGISTRY_CACHE_VERSION_MAX_SIZE", "1");
    }
}
```

- [ ] **Step 2: Verify the file compiles**

```bash
cd server && mvn -B -ntp compile -DskipTests -Dspotless.check.skip=true
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add server/src/test/java/com/roomelephant/porthole/it/infra/PortholeContainer.java
git commit -m "Rewrite PortholeContainer as standard GenericContainer"
```

---

## Task 2: Rewrite IntegrationTestBase with embedded WireMock

**Files:**
- Modify: `server/src/test/java/com/roomelephant/porthole/it/infra/IntegrationTestBase.java`

- [ ] **Step 1: Replace the entire file with this content**

```java
package com.roomelephant.porthole.it.infra;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.http.ResponseEntity;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class IntegrationTestBase {

    protected static final String TEST_LOCAL_IMAGE_TAG = "my-local-image:1.0";
    protected static final String TEST_APP_CONTAINER_NAME = "porthole-test-app";
    protected static final String TEST_NO_PORTS_CONTAINER_NAME = "porthole-test-no-ports";
    protected static final String TEST_STOPPED_CONTAINER_NAME = "porthole-test-stopped";
    protected static final String TEST_LOCAL_CONTAINER_NAME = "porthole-test-local";
    protected static final String BUSYBOX_IMAGE = "busybox:1.37.0-uclibc";
    protected static final String BUSYBOX_LATEST_IMAGE = "busybox:latest";

    private static final String[] CONTAINER_CMD = {"sh", "-c", "while true; do sleep 3600; done"};

    protected static GenericContainer<?> testAppContainer;
    protected static GenericContainer<?> localContainer;
    protected static GenericContainer<?> noPortsContainer;

    protected static final WireMockServer wireMockServer;
    protected static final PortholeContainer porthole;
    private static final WireMock wireMockClient;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        porthole = new PortholeContainer(wireMockServer.port());
        porthole.start();

        wireMockClient = new WireMock("localhost", wireMockServer.port());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            porthole.stop();
            wireMockServer.stop();
        }));
    }

    private final org.springframework.web.client.RestTemplate restTemplate = createRestTemplate();

    private static org.springframework.web.client.RestTemplate createRestTemplate() {
        var template = new org.springframework.web.client.RestTemplate();
        template.setErrorHandler(response -> response.getStatusCode().is5xxServerError());
        return template;
    }

    protected static WireMock wireMockClient() {
        return wireMockClient;
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        if (testInfo.getTestMethod().isPresent()
                && testInfo.getTestMethod().get().isAnnotationPresent(RunWithoutContainers.class)) {
            cleanupDocker();
            return;
        }
        if (areContainersRunning()) {
            return;
        }
        createContainers();
    }

    @AfterEach
    protected void tearDown() {
        List<ServeEvent> allServeEvents = wireMockClient().getServeEvents();

        List<ServeEvent> unmatchedEvents =
                allServeEvents.stream().filter(e -> !e.getWasMatched()).toList();

        if (!unmatchedEvents.isEmpty()) {
            String details = unmatchedEvents.stream()
                    .map(e -> e.getRequest().getMethod() + " " + e.getRequest().getUrl())
                    .toList()
                    .toString();
            wireMockClient().resetToDefaultMappings();
            throw new AssertionError(
                    "The following requests were made but not matched by any stub: " + details);
        }

        List<StubMapping> allStubs = wireMockClient().listAllStubMappings().getMappings();
        List<StubMapping> unusedStubs = allStubs.stream()
                .filter(stub -> allServeEvents.stream()
                        .noneMatch(event -> event.getStubMapping() != null
                                && event.getStubMapping().getId().equals(stub.getId())))
                .toList();

        if (!unusedStubs.isEmpty()) {
            wireMockClient().resetToDefaultMappings();
            throw new AssertionError("The following stubs were defined but never matched: " + unusedStubs);
        }

        wireMockClient().resetToDefaultMappings();
    }

    protected @NotNull ResponseEntity<String> fetch(String url) {
        return fetch(url, String.class);
    }

    protected <T> @NotNull ResponseEntity<T> fetch(String url, Class<T> responseType) {
        return restTemplate.getForEntity(porthole.baseUrl() + url, responseType);
    }

    private boolean areContainersRunning() {
        return testAppContainer != null
                && testAppContainer.isRunning()
                && noPortsContainer != null
                && noPortsContainer.isRunning()
                && localContainer != null
                && localContainer.isRunning();
    }

    private void cleanupDocker() {
        if (testAppContainer != null) {
            testAppContainer.stop();
            testAppContainer = null;
        }
        if (localContainer != null) {
            localContainer.stop();
            localContainer = null;
        }
        if (noPortsContainer != null) {
            noPortsContainer.stop();
            noPortsContainer = null;
        }
        try {
            DockerClientFactory.instance()
                    .client()
                    .removeContainerCmd(TEST_STOPPED_CONTAINER_NAME)
                    .withForce(true)
                    .exec();
        } catch (Exception ignored) {
        }
    }

    private void createContainers() {
        var dc = DockerClientFactory.instance().client();

        // Tag a local image (simulates an image not from a public registry)
        dc.tagImageCmd(BUSYBOX_IMAGE, "my-local-image", "1.0").exec();

        // App Container (running, has public port)
        testAppContainer = new GenericContainer<>(BUSYBOX_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_APP_CONTAINER_NAME))
                .withCommand(CONTAINER_CMD)
                .withExposedPorts(8080);
        testAppContainer.start();

        // Stopped Container (created but never started — uses raw docker-java because
        // GenericContainer.stop() also removes the container)
        try {
            dc.removeContainerCmd(TEST_STOPPED_CONTAINER_NAME).withForce(true).exec();
        } catch (Exception ignored) {
        }
        dc.createContainerCmd(BUSYBOX_IMAGE)
                .withName(TEST_STOPPED_CONTAINER_NAME)
                .withExposedPorts(ExposedPort.tcp(8081))
                .exec();

        // Local Image Container (running, has public port, uses the locally tagged image)
        localContainer = new GenericContainer<>(DockerImageName.parse(TEST_LOCAL_IMAGE_TAG))
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_LOCAL_CONTAINER_NAME))
                .withCommand(CONTAINER_CMD)
                .withExposedPorts(8082)
                .withImagePullPolicy(imageName -> false);
        localContainer.start();

        // No-Ports Container (running, no exposed ports)
        noPortsContainer = new GenericContainer<>(BUSYBOX_LATEST_IMAGE)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(TEST_NO_PORTS_CONTAINER_NAME))
                .withCommand(CONTAINER_CMD);
        noPortsContainer.start();
    }
}
```

- [ ] **Step 2: Verify the file compiles**

```bash
cd server && mvn -B -ntp compile -DskipTests -Dspotless.check.skip=true
```

Expected: `BUILD SUCCESS`. If there are import errors, check that `com.github.dockerjava.api.model.ExposedPort` is available — it comes from the `docker-java` dependency already in `pom.xml`.

- [ ] **Step 3: Commit**

```bash
git add server/src/test/java/com/roomelephant/porthole/it/infra/IntegrationTestBase.java
git commit -m "Rewrite IntegrationTestBase with embedded WireMock"
```

---

## Task 3: Delete DockerInfrastructure.java and DinDContainer.java

**Files:**
- Delete: `server/src/test/java/com/roomelephant/porthole/it/infra/DockerInfrastructure.java`
- Delete: `server/src/test/java/com/roomelephant/porthole/it/infra/DinDContainer.java`

- [ ] **Step 1: Delete both files**

```bash
rm server/src/test/java/com/roomelephant/porthole/it/infra/DockerInfrastructure.java
rm server/src/test/java/com/roomelephant/porthole/it/infra/DinDContainer.java
```

- [ ] **Step 2: Verify the project compiles**

```bash
cd server && mvn -B -ntp compile -DskipTests -Dspotless.check.skip=true
```

Expected: `BUILD SUCCESS`. If `DockerConnectionFailureIT` still references `DockerInfrastructure`, it will fail — that's fixed in Task 5.

Note: compile may fail if `DockerConnectionFailureIT` still imports `DockerInfrastructure`. Check the error message. If that's the only error, proceed to Task 5 before committing.

- [ ] **Step 3: Commit**

```bash
git add -u server/src/test/java/com/roomelephant/porthole/it/infra/
git commit -m "Delete DinD infrastructure: DockerInfrastructure and DinDContainer"
```

---

## Task 4: Update ContainersEndpointIT — remove exact count assertions

**Files:**
- Modify: `server/src/test/java/com/roomelephant/porthole/it/controller/ContainersEndpointIT.java`

Background: with host Docker, Porthole sees all containers on the daemon (including Porthole itself and Ryuk), so `hasSize(N)` assertions are fragile. Replace with name-based presence/absence checks.

- [ ] **Step 1: Update `shouldReturnEmptyListWhenNoContainers` — check specific names are absent instead of total size = 0**

Replace:
```java
void shouldReturnEmptyListWhenNoContainers() {
    Map<String, ContainerDTO> containers = fetchContainers(true, true);

    assertThat(containers).size().isEqualTo(0);
}
```

With:
```java
void shouldReturnEmptyListWhenNoContainers() {
    Map<String, ContainerDTO> containers = fetchContainers(true, true);

    assertThat(containers).doesNotContainKey(TEST_APP_CONTAINER_NAME);
    assertThat(containers).doesNotContainKey(TEST_NO_PORTS_CONTAINER_NAME);
    assertThat(containers).doesNotContainKey(TEST_STOPPED_CONTAINER_NAME);
    assertThat(containers).doesNotContainKey(TEST_LOCAL_CONTAINER_NAME);
}
```

- [ ] **Step 2: Update `shouldReturnRunningContainers` — remove `hasSize(2)`**

Remove this line:
```java
assertThat(containersByName).hasSize(2);
```

Keep all other assertions in the method unchanged.

- [ ] **Step 3: Update `shouldShowContainersWithoutPorts` — remove both `hasSize` calls**

Remove:
```java
assertThat(containersByName).hasSize(2);
```
(first occurrence, after `fetchContainers(false, false)`)

Remove:
```java
assertThat(containersByName).hasSize(3);
```
(second occurrence, after `fetchContainers(true, false)`)

Keep all other assertions unchanged.

- [ ] **Step 4: Update `shouldShowStoppedContainers` — remove both `hasSize` calls**

Remove:
```java
assertThat(containersByName).hasSize(2);
```
(after `fetchContainers(false, false)`)

Remove:
```java
assertThat(containersByName).hasSize(4);
```
(after `fetchContainers(true, true)`)

Keep all other assertions unchanged.

- [ ] **Step 5: Verify the file compiles**

```bash
cd server && mvn -B -ntp compile -DskipTests -Dspotless.check.skip=true
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add server/src/test/java/com/roomelephant/porthole/it/controller/ContainersEndpointIT.java
git commit -m "Replace exact container count assertions with name-based checks"
```

---

## Task 5: Rewrite DockerConnectionFailureIT

**Files:**
- Modify: `server/src/test/java/com/roomelephant/porthole/it/resilience/DockerConnectionFailureIT.java`

Background: the current implementation creates Porthole containers inside DinD using raw docker-java. The new approach starts Porthole via TC with custom socket configurations on the host filesystem. `WhenSocketMissing` points to a non-existent path. `WhenPermissionDenied` creates a temp file, sets it to `000` permissions, and bind-mounts it. Porthole runs as `nonroot` (UID 65532) per the Dockerfile so it cannot access a `000`-permission file.

- [ ] **Step 1: Replace the entire file with this content**

```java
package com.roomelephant.porthole.it.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roomelephant.porthole.it.infra.PortholeContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

@Order(1)
class DockerConnectionFailureIT {

    private static final WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(wireMockServer::stop));
    }

    private GenericContainer<?> porthole;
    private String portholeBaseUrl;
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(response -> false);
    }

    @AfterEach
    void tearDown() {
        if (porthole != null && porthole.isRunning()) {
            porthole.stop();
            porthole = null;
        }
        portholeBaseUrl = null;
    }

    private String baseUrlFor(GenericContainer<?> container) {
        return "http://localhost:" + container.getMappedPort(PortholeContainer.PORTHOLE_PORT);
    }

    @Nested
    class WhenSocketMissing {

        @BeforeEach
        void startBrokenPorthole() {
            porthole = PortholeContainer.withCustomSocket(
                    "/tmp/missing-docker.sock", wireMockServer.port());
            porthole.start();
            portholeBaseUrl = baseUrlFor(porthole);
        }

        @Test
        void ShouldReturnDownWhenSocketMissingOnHealth() {
            ResponseEntity<HealthResponse> response =
                    restTemplate.getForEntity(portholeBaseUrl + "/actuator/health/docker", HealthResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            HealthResponse health = response.getBody();
            assertThat(health).isNotNull();
            assertThat(health.status()).isEqualTo("DOWN");
            assertThat(health.details()).containsEntry("Error connecting to docker", "No such file or directory");
        }

        @Test
        void shouldReturn502WhenSocketMissingOnContainers() {
            ResponseEntity<String> response =
                    restTemplate.getForEntity(portholeBaseUrl + "/api/containers", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        @Test
        void shouldReturn502WhenSocketMissingOnVersion() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    portholeBaseUrl + "/api/containers/random-id/version", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }
    }

    @Nested
    class WhenPermissionDenied {

        private Path lockedSocketPath;

        @BeforeEach
        void startBrokenPorthole() throws Exception {
            lockedSocketPath = Files.createTempFile("locked-docker", ".sock");
            Files.setPosixFilePermissions(lockedSocketPath, Set.of());

            porthole = PortholeContainer.withCustomSocket(
                            "/docker-sockets/locked.sock", wireMockServer.port())
                    .withFileSystemBind(
                            lockedSocketPath.toString(),
                            "/docker-sockets/locked.sock",
                            BindMode.READ_WRITE);
            porthole.start();
            portholeBaseUrl = baseUrlFor(porthole);
        }

        @AfterEach
        void deleteLocked() throws Exception {
            if (lockedSocketPath != null) {
                Files.deleteIfExists(lockedSocketPath);
            }
        }

        @Test
        void shouldReturnDownWhenPermissionDeniedOnHealth() {
            ResponseEntity<HealthResponse> response =
                    restTemplate.getForEntity(portholeBaseUrl + "/actuator/health/docker", HealthResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            HealthResponse health = response.getBody();
            assertThat(health).isNotNull();
            assertThat(health.status()).isEqualTo("DOWN");
            assertThat(health.details()).containsEntry("Error connecting to docker", "Permission denied");
        }

        @Test
        void shouldReturn502WhenPermissionDeniedOnContainers() {
            ResponseEntity<String> response =
                    restTemplate.getForEntity(portholeBaseUrl + "/api/containers", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        @Test
        void shouldReturn502WhenPermissionDeniedOnVersion() {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    portholeBaseUrl + "/api/containers/random-id/version", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }
    }

    record HealthResponse(String status, Map<String, Object> details) {}
}
```

- [ ] **Step 2: Verify the file compiles**

```bash
cd server && mvn -B -ntp compile -DskipTests -Dspotless.check.skip=true
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add server/src/test/java/com/roomelephant/porthole/it/resilience/DockerConnectionFailureIT.java
git commit -m "Rewrite DockerConnectionFailureIT using host socket scenarios"
```

---

## Task 6: Comment out DockerHealthIT unreachable test

**Files:**
- Modify: `server/src/test/java/com/roomelephant/porthole/it/health/DockerHealthIT.java`

Background: `shouldReturnDownWhenDockerIsUnreachable` calls `pauseDocker()` which requires pausing the DinD container — no equivalent exists without DinD. Deferred for future investigation.

- [ ] **Step 1: Comment out `shouldReturnDownWhenDockerIsUnreachable` and remove its imports**

Wrap the method in a block comment:

```java
    // TODO: revisit when a clean "socket present but unresponsive" mechanism is available
    // The test requires pausing a Docker daemon mid-run, which is not possible without DinD.
    //    @Test
    //    @Order(999)
    //    @RunWithoutContainers
    //    void shouldReturnDownWhenDockerIsUnreachable() {
    //        pauseDocker();
    //
    //        try {
    //            ResponseEntity<String> response = fetch("/actuator/health");
    //
    //            assertThat(response.getStatusCode().value()).isEqualTo(SERVICE_UNAVAILABLE.value());
    //            assertThat(response.getBody()).contains("\"status\":\"DOWN\"");
    //            assertThat(response.getBody())
    //                    .contains(
    //                            "\"docker\":{\"details\":{\"Unexpected exception\":\"java.net.SocketTimeoutException: Read timed out\"},\"status\":\"DOWN\"}");
    //        } finally {
    //            unpauseDocker();
    //        }
    //    }
```

Also remove the unused import `import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;` if Spotless complains about it.

- [ ] **Step 2: Verify the file compiles**

```bash
cd server && mvn -B -ntp compile -DskipTests -Dspotless.check.skip=true
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add server/src/test/java/com/roomelephant/porthole/it/health/DockerHealthIT.java
git commit -m "Comment out DockerHealthIT unreachable test (requires DinD to pause daemon)"
```

---

## Task 7: Simplify the CI workflow

**Files:**
- Modify: `.github/workflows/reusable-server.yml`

- [ ] **Step 1: Remove the three DinD/cache steps**

Remove these steps entirely from the workflow (lines ~30–51 in the current file):

```yaml
      - name: Cache Host Docker Images
        id: cache-host-docker
        uses: actions/cache@v4
        with:
          path: host-docker-images
          key: host-docker-images-${{ runner.os }}-docker-29.1.4-dind-ryuk-0.13.0

      - name: Load Cached Docker Images
        if: steps.cache-host-docker.outputs.cache-hit == 'true'
        run: |
          docker load -i host-docker-images/docker-dind.tar
          docker load -i host-docker-images/ryuk.tar

      - name: Pull and Save Docker Images
        if: steps.cache-host-docker.outputs.cache-hit != 'true'
        run: |
          mkdir -p host-docker-images
          docker pull docker:29.1.4-dind
          docker save -o host-docker-images/docker-dind.tar docker:29.1.4-dind
          
          docker pull testcontainers/ryuk:0.13.0
          docker save -o host-docker-images/ryuk.tar testcontainers/ryuk:0.13.0

      - name: Cache Docker-in-Docker images
        uses: actions/cache@v4
        with:
          path: docker-cache
          key: docker-images-v2-${{ runner.os }}-${{ hashFiles('server/pom.xml') }}
          restore-keys: |
            docker-images-v2-${{ runner.os }}-
```

- [ ] **Step 2: Add the `docker build` step between "Build native binary" and "Check code formatting"**

Add this step after the "Build native binary" step:

```yaml
      - name: Build integration test Docker image
        run: |
          cp server/target/porthole porthole
          docker build -t porthole-it:latest -f docker/Dockerfile .
          rm porthole
```

- [ ] **Step 3: Update the "Run integration tests" step — replace env block**

Current env block:
```yaml
        env:
          CI: true
          DOCKER_CACHE_PATH: ${{ github.workspace }}/docker-cache
```

Replace with:
```yaml
        env:
          PORTHOLE_IT_IMAGE_TAG: porthole-it:latest
```

- [ ] **Step 4: Remove the "Fix cache permissions" step entirely**

Remove:
```yaml
      - name: Fix cache permissions
        if: always()
        run: |
          if [ -d "docker-cache" ]; then
            # Remove special files (block/char devices, sockets, pipes) and overlayfs work dirs
            # Use sudo because some dirs might still be root-owned/locked
            sudo find docker-cache \( -type b -o -type c -o -type s -o -type p \) -delete
            sudo find docker-cache -name "work" -type d -exec rm -rf {} +
            
            # Fix permissions
            sudo chown -R $USER:$USER docker-cache
            sudo chmod -R a+rX docker-cache
          fi
```

- [ ] **Step 5: Verify the workflow file is valid YAML**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/reusable-server.yml'))" && echo "Valid YAML"
```

Expected: `Valid YAML`

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/reusable-server.yml
git commit -m "Simplify CI: remove DinD caching, add docker build step"
```

---

## Task 8: End-to-end verification

Prerequisites: you need a compiled native binary (`server/target/porthole`) to build the Docker image. If it doesn't exist locally (it's a Linux ELF binary), run the native compile first.

- [ ] **Step 1: Check if the native binary exists and is a Linux ELF**

```bash
file server/target/porthole 2>/dev/null || echo "Binary not found"
```

If output contains `ELF 64-bit LSB executable`, proceed to Step 2. Otherwise skip to Step 1b.

- [ ] **Step 1b (if no binary): Compile the native binary**

```bash
cd server && mvn -B -ntp -Pnative,copy-client package native:compile -DskipTests -Dspotless.check.skip=true
```

This takes 5–10 minutes on first run.

- [ ] **Step 2: Build the Porthole Docker image**

From the repo root:
```bash
cp server/target/porthole porthole
docker build -t porthole-it:latest -f docker/Dockerfile .
rm porthole
```

Expected: `Successfully tagged porthole-it:latest`

- [ ] **Step 3: Run the full integration test suite**

```bash
cd server && PORTHOLE_IT_IMAGE_TAG=porthole-it:latest mvn -B -ntp verify -Pintegration-tests -Dspotless.check.skip=true
```

Expected: `BUILD SUCCESS` with all IT tests passing.

If tests fail, check for:
- `WhenPermissionDenied` failures: verify that `Files.setPosixFilePermissions(path, Set.of())` produced a 000-permission file and that Docker bind-mounted it correctly
- Container count assertion failures: these would indicate that the `hasSize()` removals in Task 4 missed some occurrence — grep `ContainersEndpointIT.java` for `hasSize`
- `VersionEndpointIT` failures: `getDockerClient()` on `noPortsContainer` returns the TC docker client — verify it can inspect the `busybox:latest` image on the host daemon

- [ ] **Step 4: Commit if everything passes**

```bash
git add -A
git commit -m "chore: verify IT suite passes with standard Testcontainers setup"
```
