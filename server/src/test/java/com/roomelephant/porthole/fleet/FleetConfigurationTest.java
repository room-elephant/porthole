package com.roomelephant.porthole.fleet;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FleetConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FleetConfiguration.class)
            .withPropertyValues(
                    "porthole.docker.connections[0].id=walker",
                    "porthole.docker.connections[0].name=Walker",
                    "porthole.docker.connections[0].host=unix:///var/run/docker.sock",
                    "porthole.docker.connections[0].published-host=192.168.50.6",
                    "porthole.docker.connections[0].connection-timeout=2s",
                    "porthole.docker.connections[0].response-timeout=3s",
                    "porthole.docker.connections[1].id=mike",
                    "porthole.docker.connections[1].name=Mike",
                    "porthole.docker.connections[1].host=tcp://mike:2375");

    @Test
    void catalogFollowsConnectionsWithDeadlineAndLocalFlag() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SourceCatalog.class).sources())
                    .containsExactly(
                            new Source("walker", "Walker", "192.168.50.6", true, Duration.ofSeconds(5)),
                            new Source("mike", "Mike", null, false, Duration.ofSeconds(65)));
        });
    }

    @Test
    void settingsCarryConfiguredIntervals() {
        runner.withPropertyValues("porthole.docker.poll-interval=5s", "porthole.docker.snapshot-retention=1h")
                .run(context -> {
                    FleetSettings settings = context.getBean(FleetSettings.class);
                    assertThat(settings.pollInterval()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(settings.snapshotRetention()).isEqualTo(Duration.ofHours(1));
                    assertThat(settings.nudgeDelay()).isEqualTo(Duration.ofMillis(500));
                    assertThat(settings.minBackoff()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(settings.maxBackoff()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(settings.stableSubscription()).isEqualTo(Duration.ofSeconds(30));
                });
    }
}
