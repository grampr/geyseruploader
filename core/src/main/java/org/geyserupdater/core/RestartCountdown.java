package org.geyserupdater.core;

import org.geyserupdater.core.logging.LogAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** One restart per plugin lifecycle, shared by all update checks and platforms. */
public final class RestartCountdown implements AutoCloseable {
    @FunctionalInterface
    public interface Task {
        void cancel();
    }

    @FunctionalInterface
    public interface Scheduler {
        // Implementations enqueue the action on their platform's appropriate executor.
        Task schedule(Runnable action, long delaySeconds);
    }

    private final Scheduler scheduler;
    private final Consumer<String> broadcast;
    private final Consumer<String> restart;
    private final LogAdapter log;
    private final List<Task> tasks = new ArrayList<>();
    private Task restartTask;
    private final Set<Integer> pendingNotifications = new HashSet<>();
    private boolean requested;
    private boolean executed;
    private boolean closed;
    private long generation;

    public RestartCountdown(Scheduler scheduler, Consumer<String> broadcast, Consumer<String> restart, LogAdapter log) {
        this.scheduler = scheduler;
        this.broadcast = broadcast;
        this.restart = restart;
        this.log = log;
    }

    public synchronized boolean request(Config config) {
        String command = config.postUpdate.restartCommand;
        if (closed || requested || !config.enabled || !config.postUpdate.runRestartCommand
                || command == null || command.isBlank()) return false;

        requested = true;
        long requestGeneration = ++generation;
        int delay = Math.max(0, config.restart.delay);
        Config.Notifications notifications = config.restart.notifications;
        String message = notifications.message;
        try {
            if (delay > 0 && notifications.enabled && message != null && !message.isBlank()) {
                TreeSet<Integer> times = new TreeSet<>(Collections.reverseOrder());
                for (Integer time : notifications.times) {
                    if (time != null && time > 0 && time <= delay) times.add(time);
                }
                pendingNotifications.addAll(times);
                for (int time : times) {
                    tasks.add(scheduler.schedule(() -> notifyPlayers(requestGeneration, time, message), (long) delay - time));
                }
            }
            restartTask = scheduler.schedule(() -> execute(requestGeneration, command), delay);
            return true;
        } catch (RuntimeException e) {
            generation++;
            cancelTasks();
            // Allow a later completed update to retry if no command was dispatched.
            requested = executed;
            log.error("Could not schedule automatic restart", e);
            return false;
        }
    }

    private synchronized void notifyPlayers(long requestGeneration, int seconds, String message) {
        if (requestGeneration != generation || closed || executed || !pendingNotifications.remove(seconds)) return;
        try {
            broadcast.accept(message.replace("{seconds}", String.valueOf(seconds)));
        } catch (RuntimeException e) {
            // A notification failure must not cancel the remaining countdown or the restart.
            log.error("Could not broadcast restart countdown", e);
        }
    }

    private synchronized void execute(long requestGeneration, String command) {
        if (requestGeneration != generation || closed || executed) return;
        executed = true;
        // Velocity cancellation interrupts running tasks. Never cancel our own restart callback.
        restartTask = null;
        cancelTasks();
        try {
            restart.accept(command);
        } catch (RuntimeException e) {
            // Keep requested/executed set even when a command fails: never execute it twice.
            log.error("Could not execute automatic restart command", e);
        }
    }

    private void cancelTasks() {
        if (restartTask != null) {
            tasks.add(restartTask);
            restartTask = null;
        }
        for (Task task : tasks) {
            try {
                task.cancel();
            } catch (RuntimeException e) {
                log.error("Could not cancel restart task", e);
            }
        }
        tasks.clear();
        pendingNotifications.clear();
    }

    @Override
    public synchronized void close() {
        closed = true;
        cancelTasks();
    }
}
