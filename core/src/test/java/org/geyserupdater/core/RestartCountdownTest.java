package org.geyserupdater.core;

import org.geyserupdater.core.logging.LogAdapter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RestartCountdownTest {
    private final FakeScheduler scheduler = new FakeScheduler();
    private final List<String> messages = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final LogAdapter log = new LogAdapter() {
        public void info(String message) { }
        public void warn(String message) { }
        public void error(String message, Throwable error) { errors.add(message); }
    };
    private final RestartCountdown countdown = new RestartCountdown(scheduler,
            messages::add, commands::add, log);

    private Config config(int delay) {
        Config cfg = new Config();
        cfg.postUpdate.runRestartCommand = true;
        cfg.restart.delay = delay;
        cfg.restart.notifications.message = "Restart in {seconds}s / {seconds}";
        return cfg;
    }

    @Test
    public void notifiesAtDefaultThresholdsAndRestartsOnlyAfterFullDelay() {
        assertTrue(countdown.request(config(120)));
        assertTrue(messages.isEmpty());
        assertTrue(commands.isEmpty());
        scheduler.advanceTo(0);
        assertEquals(List.of("Restart in 120s / 120"), messages);
        scheduler.advanceTo(59);
        assertEquals(1, messages.size());
        scheduler.advanceTo(60);
        assertEquals("Restart in 60s / 60", messages.get(1));
        scheduler.advanceTo(119);
        assertEquals(List.of(120, 60, 30, 10, 5, 4, 3, 2, 1).stream()
                .map(s -> "Restart in " + s + "s / " + s).toList(), messages);
        assertTrue(commands.isEmpty());
        scheduler.advanceTo(120);
        assertEquals(List.of("restart"), commands);
        assertFalse(countdown.request(config(120)));
    }

    @Test
    public void sortsDeduplicatesAndIgnoresOutOfRangeThresholds() {
        Config cfg = config(5);
        cfg.restart.notifications.times = Arrays.asList(1, 5, 3, 3, 10, 0, -2, null);
        countdown.request(cfg);
        assertEquals(4, scheduler.entries.size());
        scheduler.advanceTo(5);
        assertEquals(List.of("Restart in 5s / 5", "Restart in 3s / 3", "Restart in 1s / 1"), messages);
        assertEquals(1, commands.size());
    }

    @Test
    public void zeroDelaySchedulesOnlyImmediateRestartAndIgnoresRepeatedCallbacks() {
        assertTrue(countdown.request(config(0)));
        assertFalse(countdown.request(config(0)));
        assertEquals(1, scheduler.entries.size());
        Runnable callback = scheduler.entries.get(0).action;
        scheduler.advanceTo(0);
        assertFalse("Do not interrupt the running restart task on Velocity", scheduler.entries.get(0).cancelled);
        callback.run();
        assertEquals(List.of("restart"), commands);
        assertTrue(messages.isEmpty());
        assertFalse(countdown.request(config(0)));
    }

    @Test
    public void respectsRestartSwitchAndBlankCommand() {
        Config cfg = config(10);
        cfg.postUpdate.runRestartCommand = false;
        assertFalse(countdown.request(cfg));
        cfg.postUpdate.runRestartCommand = true;
        cfg.postUpdate.restartCommand = " ";
        assertFalse(countdown.request(cfg));
        cfg.postUpdate.restartCommand = null;
        assertFalse(countdown.request(cfg));
        cfg.postUpdate.restartCommand = "restart";
        cfg.enabled = false;
        assertFalse(countdown.request(cfg));
        assertTrue(scheduler.entries.isEmpty());
    }

    @Test
    public void notificationSwitchEmptyTimesAndBlankMessageDoNotDisableRestart() {
        for (int mode = 0; mode < 3; mode++) {
            FakeScheduler clock = new FakeScheduler();
            RestartCountdown timer = new RestartCountdown(clock, messages::add, commands::add, log);
            Config cfg = config(20);
            if (mode == 0) cfg.restart.notifications.enabled = false;
            if (mode == 1) cfg.restart.notifications.times = List.of();
            if (mode == 2) cfg.restart.notifications.message = " ";
            timer.request(cfg);
            assertEquals(1, clock.entries.size());
            clock.advanceTo(19);
            assertEquals(mode, commands.size());
            clock.advanceTo(20);
        }
        assertEquals(3, commands.size());
        assertTrue(messages.isEmpty());
    }

    @Test
    public void closeCancelsTasksAndRejectsAlreadyDequeuedCallbacksAndLateUpdates() {
        countdown.request(config(120));
        List<Runnable> callbacks = scheduler.entries.stream().map(entry -> entry.action).toList();
        countdown.close();
        assertTrue(scheduler.entries.stream().allMatch(entry -> entry.cancelled));
        callbacks.forEach(Runnable::run);
        scheduler.advanceTo(120);
        assertTrue(messages.isEmpty());
        assertTrue(commands.isEmpty());
        assertFalse(countdown.request(config(120)));
    }

    @Test
    public void concurrentUpdatesScheduleExactlyOneCountdown() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var gate = new CountDownLatch(1);
        var accepted = new AtomicInteger();
        Config cfg = config(120);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 32; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        gate.await();
                        if (countdown.request(cfg)) accepted.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }));
            }
            gate.countDown();
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
            assertEquals(1, accepted.get());
            assertEquals(10, scheduler.entries.size());
            scheduler.advanceTo(120);
            assertEquals(9, messages.size());
            assertEquals(1, commands.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void failedSchedulingCancelsPartialTasksAndStaleCallbacksCannotAffectRetry() {
        scheduler.failAt = 2;
        assertFalse(countdown.request(config(120)));
        Runnable stale = scheduler.entries.get(0).action;
        assertTrue(scheduler.entries.get(0).cancelled);
        scheduler.failAt = -1;
        assertTrue(countdown.request(config(120)));
        stale.run();
        assertTrue(messages.isEmpty());
        scheduler.advanceTo(120);
        assertEquals(9, messages.size());
        assertEquals(1, commands.size());
        assertEquals(1, errors.size());
    }

    @Test
    public void notificationFailureDoesNotPreventRestartAndCommandFailureIsNotRetried() {
        RestartCountdown timer = new RestartCountdown(scheduler,
                message -> { throw new IllegalStateException("disconnected"); },
                command -> { commands.add(command); throw new IllegalStateException("unknown command"); }, log);
        timer.request(config(1));
        Runnable restart = scheduler.entries.get(1).action;
        scheduler.advanceTo(1);
        restart.run();
        assertFalse(timer.request(config(1)));
        assertEquals(1, commands.size());
        assertEquals(2, errors.size());
    }

    private static class FakeScheduler implements RestartCountdown.Scheduler {
        final List<Entry> entries = new ArrayList<>();
        long now;
        int calls;
        int failAt = -1;

        public RestartCountdown.Task schedule(Runnable action, long delaySeconds) {
            if (++calls == failAt) throw new IllegalStateException("scheduler stopped");
            Entry entry = new Entry(now + delaySeconds, action);
            entries.add(entry);
            return () -> entry.cancelled = true;
        }

        void advanceTo(long target) {
            while (true) {
                Entry next = entries.stream().filter(e -> !e.cancelled && !e.ran && e.due <= target)
                        .min(Comparator.comparingLong(e -> e.due)).orElse(null);
                if (next == null) break;
                now = next.due;
                next.ran = true;
                next.action.run();
            }
            now = target;
        }
    }

    private static class Entry {
        final long due;
        final Runnable action;
        boolean cancelled;
        boolean ran;
        Entry(long due, Runnable action) { this.due = due; this.action = action; }
    }
}
