# Native Image Hints Auto-Generation via Integration Tests

**Date:** 2026-05-23
**Status:** Approved

## Problem

`reflect-config.json` (and companion native-image hint files) are manually maintained under
`server/src/main/resources/META-INF/native-image/com.roomelephant/porthole/`.
Manual maintenance is error-prone: new reflection usages silently slip through, and the file
tends to drift from what the application actually needs.

## Goal

Use the existing integration test suite to automatically generate native-image hint files via the
GraalVM native-image agent, replacing the manually maintained `reflect-config.json` with a
generated, test-verified set of hint files.

## Architecture

A dedicated Maven profile (`generate-native-hints`) runs the integration tests against a
GraalVM-JDK-based app container with the native-image agent enabled. The agent monitors all
reflection, proxy, JNI, and resource access inside the app JVM. A host directory (the
source-tree native-image resources path) is mounted into the container; the agent writes files
there directly. When the container stops gracefully (SIGTERM → JVM shutdown hook), all hint
files are flushed to the mounted path. No post-test copy step is required.

Regular IT runs (`-Pintegration-tests`) are unaffected: the GraalVM base image is used, but the
agent is only activated when `native.agent.output.dir` is set, which only happens under the
generation profile.

```
mvn verify -Pgenerate-native-hints
    │
    ├── failsafe passes system property: native.agent.output.dir=<source-tree path>
    │
    ├── PortholeContainer (if property set):
    │     ├── env: JAVA_TOOL_OPTIONS=-agentlib:native-image-agent=config-output-dir=/tmp/native-hints
    │     └── volume: <source-tree path> → /tmp/native-hints (READ_WRITE)
    │
    ├── ITs run normally (all *IT.java classes)
    │
    └── on container stop → agent writes hint files → appear on host immediately
```

## Component Changes

### 1. `docker/Dockerfile.it`

Swap the base image from `eclipse-temurin:25-jre-jammy` to a GraalVM JDK 25 community image
(e.g., `ghcr.io/graalvm/jdk-community:25`). The native-image agent ships with the GraalVM JDK;
no additional installation is needed. All other Dockerfile content remains identical.

### 2. `server/src/test/java/.../infra/PortholeContainer.java`

Add a private static `applyNativeAgentIfEnabled(GenericContainer<?> container)` method called
at the end of the constructor (before `waitingFor`). It reads
`System.getProperty("native.agent.output.dir")`; if non-null it:

- Adds env var: `JAVA_TOOL_OPTIONS=-agentlib:native-image-agent=config-output-dir=/tmp/native-hints`
- Adds volume bind: `outputDir → /tmp/native-hints` with `READ_WRITE`

`withCustomSocket` and `applyRegistryEnv` are not modified.

### 3. `server/pom.xml`

New profile `generate-native-hints`:

```xml
<profile>
    <id>generate-native-hints</id>
    <properties>
        <jacoco.skip>true</jacoco.skip>
        <spotless.check.skip>true</spotless.check.skip>
    </properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <skipTests>true</skipTests>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                    <includes>
                        <include>**/*IT.java</include>
                    </includes>
                    <systemPropertyVariables>
                        <native.agent.output.dir>${project.basedir}/src/main/resources/META-INF/native-image/com.roomelephant/porthole</native.agent.output.dir>
                    </systemPropertyVariables>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

### 4. Native-image resource directory

`src/main/resources/META-INF/native-image/com.roomelephant/porthole/`

The existing `reflect-config.json` is replaced on each generation run. The agent additionally
writes:

- `proxy-config.json`
- `resource-config.json`
- `jni-config.json`
- `serialization-config.json`

All files in this directory are automatically picked up by the `native-maven-plugin` (`-Pnative`).

## Coverage

All existing `*IT.java` classes run under the generation profile:

| Test class | Reflection paths exercised |
|---|---|
| `DockerHealthIT` | Docker client startup, health state deserialization |
| `ContainersEndpointIT` | Container listing, image inspection, port mapping |
| `VersionEndpointIT` | Image inspection, registry HTTP client |
| `DockerConnectionFailureIT` | Docker client error paths |

The docker-java model classes (the bulk of the existing `reflect-config.json`) are exercised via
Docker API response deserialization during container listing and inspection, which all container
endpoint tests trigger.

## Usage

```bash
# Regenerate hint files (run after adding new features that use reflection)
mvn verify -Pgenerate-native-hints

# Build native image using the generated hints
mvn -Pnative,build-client,copy-client package
```

## Decisions

- **Replace, not merge:** Generated output fully replaces existing hint files. Any class not
  reachable from the IT suite will not appear — keeping the file honest about actual coverage.
- **Single Dockerfile.it:** GraalVM JDK is used for all IT runs. The agent is a no-op unless
  `JAVA_TOOL_OPTIONS` is set, so regular ITs have no overhead beyond the image size difference.
- **`JAVA_TOOL_OPTIONS` over entrypoint changes:** The env var is picked up automatically by
  any JVM invocation without touching the entrypoint script.
