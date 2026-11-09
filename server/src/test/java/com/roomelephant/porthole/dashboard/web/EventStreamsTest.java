package com.roomelephant.porthole.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.roomelephant.porthole.dashboard.DashboardChanges;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class EventStreamsTest {
    private static final Duration HEARTBEAT = Duration.ofSeconds(15);

    private final DashboardChanges changes = mock(DashboardChanges.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final Runnable unsubscribe = mock(Runnable.class);
    private final List<RecordingEmitter> created = new ArrayList<>();
    private EventStreams streams;

    @BeforeEach
    void setUp() {
        when(changes.revision()).thenReturn(42L);
        when(changes.subscribe(any())).thenReturn(unsubscribe);
        when(scheduler.scheduleWithFixedDelay(any(), eq(15_000L), eq(15_000L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(_ -> mock(ScheduledFuture.class));
        streams = new EventStreams(changes, scheduler, 2, HEARTBEAT, () -> {
            RecordingEmitter emitter = new RecordingEmitter();
            created.add(emitter);
            return emitter;
        });
        streams.start();
    }

    private LongConsumer subscriber() {
        ArgumentCaptor<LongConsumer> captor = ArgumentCaptor.forClass(LongConsumer.class);
        verify(changes).subscribe(captor.capture());
        return captor.getValue();
    }

    private Runnable heartbeat() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleWithFixedDelay(captor.capture(), eq(15_000L), eq(15_000L), eq(TimeUnit.MILLISECONDS));
        return captor.getValue();
    }

    @Test
    void opensWithTheCurrentRevision() {
        assertThat(streams.open()).isPresent();

        assertThat(created.getFirst().sent).containsExactly("event:changed\ndata:{\"revision\":42}\nretry:5000\n\n");
    }

    @Test
    void refusesBeyondTheLimit() {
        assertThat(streams.open()).isPresent();
        assertThat(streams.open()).isPresent();

        assertThat(streams.open()).isEmpty();
        assertThat(streams.openCount()).isEqualTo(2);
    }

    @Test
    void freesASlotWhenAStreamCompletes() {
        streams.open();
        streams.open();

        created.getFirst().completion.run();

        assertThat(streams.openCount()).isEqualTo(1);
        assertThat(streams.open()).isPresent();
    }

    @Test
    void freesASlotWhenAStreamFailsOrTimesOut() {
        streams.open();
        streams.open();

        created.get(0).error.accept(new IOException("reset"));
        created.get(1).timeout.run();

        assertThat(streams.openCount()).isZero();
    }

    @Test
    void broadcastsDeliveredRevisions() {
        streams.open();
        streams.open();

        subscriber().accept(43);

        assertThat(created)
                .allSatisfy(emitter ->
                        assertThat(emitter.sent).last().isEqualTo("event:changed\ndata:{\"revision\":43}\n\n"));
    }

    @Test
    void dropsAStreamWhoseSendFails() {
        streams.open();
        streams.open();
        created.getFirst().failing = true;

        subscriber().accept(43);

        assertThat(streams.openCount()).isEqualTo(1);
        assertThat(created.get(1).sent).hasSize(2);
    }

    @Test
    void sendsKeepaliveComments() {
        streams.open();

        heartbeat().run();

        assertThat(created.getFirst().sent).last().isEqualTo(":keepalive\n\n");
    }

    @Test
    void stopCompletesEveryStream() {
        streams.open();
        streams.open();

        streams.stop();

        assertThat(created).allSatisfy(emitter -> assertThat(emitter.completed).isTrue());
        assertThat(streams.openCount()).isZero();
        assertThat(streams.isRunning()).isFalse();
        verify(unsubscribe).run();
    }

    @Test
    void startingTwiceSubscribesOnce() {
        streams.start();

        assertThat(streams.isRunning()).isTrue();
        subscriber();
        heartbeat();
    }

    @Test
    void stoppingTwiceIsHarmless() {
        streams.stop();
        streams.stop();

        verify(unsubscribe).run();
    }

    @Test
    void refusesWhileStopped() {
        streams.stop();

        assertThat(streams.open()).isEmpty();
    }

    @Test
    void refusesBeforeStart() {
        EventStreams fresh = new EventStreams(changes, scheduler, 2, HEARTBEAT, RecordingEmitter::new);

        assertThat(fresh.open()).isEqualTo(Optional.empty());
    }

    @Test
    void closeStopsTheScheduler() {
        streams.close();

        verify(scheduler).shutdownNow();
    }

    /** Records what would be written and captures the callbacks Spring would invoke. */
    static final class RecordingEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        Runnable completion = () -> {};
        Runnable timeout = () -> {};
        java.util.function.Consumer<Throwable> error = _ -> {};
        boolean failing;
        boolean completed;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failing) throw new IOException("Broken pipe");
            sent.add(builder.build().stream()
                    .map(part -> String.valueOf(part.getData()))
                    .collect(Collectors.joining()));
        }

        @Override
        public void onCompletion(Runnable callback) {
            completion = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeout = callback;
        }

        @Override
        public void onError(java.util.function.Consumer<Throwable> callback) {
            error = callback;
        }

        @Override
        public void complete() {
            completed = true;
            completion.run();
        }
    }
}
