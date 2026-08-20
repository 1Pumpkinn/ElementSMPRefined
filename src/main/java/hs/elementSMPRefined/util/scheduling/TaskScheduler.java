package hs.elementSMPRefined.util.scheduling;

import hs.elementSMPRefined.config.Constants;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Enhanced task scheduler with async support, task management, and better timing utilities.
 * Provides comprehensive scheduling capabilities with task tracking and cancellation.
 */
public final class TaskScheduler {
    private final JavaPlugin plugin;
    private final Map<String, BukkitTask> namedTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, BukkitTask>> playerTasks = new ConcurrentHashMap<>();

    public TaskScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Run a task immediately on the main thread
     */
    public BukkitTask runNow(Runnable task) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTask(plugin);
    }

    /**
     * Run a task later on the main thread
     */
    public BukkitTask runLater(Runnable task, long delayTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskLater(plugin, delayTicks);
    }

    /**
     * Run a task later in seconds
     */
    public BukkitTask runLaterSeconds(Runnable task, int seconds) {
        return runLater(task, seconds * Constants.Timing.TICKS_PER_SECOND);
    }

    /**
     * Run a repeating task on the main thread
     */
    public BukkitTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskTimer(plugin, delayTicks, periodTicks);
    }

    /**
     * Run a repeating task in seconds
     */
    public BukkitTask runTimerSeconds(Runnable task, int delaySeconds, int periodSeconds) {
        return runTimer(task,
                delaySeconds * Constants.Timing.TICKS_PER_SECOND,
                periodSeconds * Constants.Timing.TICKS_PER_SECOND
        );
    }

    /**
     * Run a task asynchronously
     */
    public BukkitTask runAsync(Runnable task) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Run a task asynchronously after a delay
     */
    public BukkitTask runAsyncLater(Runnable task, long delayTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskLaterAsynchronously(plugin, delayTicks);
    }

    /**
     * Run a repeating task asynchronously
     */
    public BukkitTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskTimerAsynchronously(plugin, delayTicks, periodTicks);
    }

    /**
     * Run a named task that can be cancelled by name
     */
    public BukkitTask runNamed(String name, Runnable task, long delayTicks) {
        cancelNamed(name); // Cancel existing task with same name
        BukkitTask bukkitTask = runLater(task, delayTicks);
        namedTasks.put(name, bukkitTask);
        return bukkitTask;
    }

    /**
     * Run a named repeating task
     */
    public BukkitTask runNamedTimer(String name, Runnable task, long delayTicks, long periodTicks) {
        cancelNamed(name); // Cancel existing task with same name
        BukkitTask bukkitTask = runTimer(task, delayTicks, periodTicks);
        namedTasks.put(name, bukkitTask);
        return bukkitTask;
    }

    /**
     * Cancel a named task
     */
    public void cancelNamed(String name) {
        BukkitTask task = namedTasks.remove(name);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Check if a named task exists
     */
    public boolean hasNamedTask(String name) {
        BukkitTask task = namedTasks.get(name);
        return task != null && !task.isCancelled();
    }

    /**
     * Run a task for a specific player
     */
    public BukkitTask runForPlayer(UUID playerId, String taskName, Runnable task, long delayTicks) {
        cancelPlayerTask(playerId, taskName);

        Map<String, BukkitTask> tasks = playerTasks.computeIfAbsent(playerId, k -> new HashMap<>());
        BukkitTask bukkitTask = runLater(task, delayTicks);
        tasks.put(taskName, bukkitTask);
        return bukkitTask;
    }

    /**
     * Run a repeating task for a specific player
     */
    public BukkitTask runTimerForPlayer(UUID playerId, String taskName, Runnable task,
                                       long delayTicks, long periodTicks) {
        cancelPlayerTask(playerId, taskName);

        Map<String, BukkitTask> tasks = playerTasks.computeIfAbsent(playerId, k -> new HashMap<>());
        BukkitTask bukkitTask = runTimer(task, delayTicks, periodTicks);
        tasks.put(taskName, bukkitTask);
        return bukkitTask;
    }

    /**
     * Cancel a specific player's task
     */
    public void cancelPlayerTask(UUID playerId, String taskName) {
        Map<String, BukkitTask> tasks = playerTasks.get(playerId);
        if (tasks != null) {
            BukkitTask task = tasks.remove(taskName);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }

    /**
     * Cancel all tasks for a specific player
     */
    public void cancelAllPlayerTasks(UUID playerId) {
        Map<String, BukkitTask> tasks = playerTasks.remove(playerId);
        if (tasks != null) {
            tasks.values().forEach(task -> {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            });
        }
    }

    /**
     * Run a task repeatedly until a condition is met
     */
    public BukkitTask runUntil(Runnable task, Supplier<Boolean> condition,
                              long delayTicks, long periodTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (condition.get()) {
                    this.cancel();
                } else {
                    task.run();
                }
            }
        }.runTaskTimer(plugin, delayTicks, periodTicks);
    }

    /**
     * Run a task repeatedly while a condition is true
     */
    public BukkitTask runWhile(Runnable task, Supplier<Boolean> condition,
                               long delayTicks, long periodTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (condition.get()) {
                    task.run();
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, delayTicks, periodTicks);
    }

    /**
     * Run a task after a condition is met
     */
    public BukkitTask runWhen(Runnable task, Supplier<Boolean> condition, long checkInterval) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (condition.get()) {
                    task.run();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, checkInterval);
    }

    /**
     * Run multiple tasks in sequence
     */
    public BukkitTask runSequence(Runnable... tasks) {
        if (tasks.length == 0) return null;

        return new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                if (index < tasks.length) {
                    tasks[index].run();
                    index++;
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Run tasks with delays between them
     */
    public BukkitTask runSequenceWithDelays(long delayBetween, Runnable... tasks) {
        if (tasks.length == 0) return null;

        return new BukkitRunnable() {
            private int index = 0;
            private long ticksSinceLast = 0;

            @Override
            public void run() {
                ticksSinceLast++;
                if (ticksSinceLast >= delayBetween) {
                    if (index < tasks.length) {
                        tasks[index].run();
                        index++;
                        ticksSinceLast = 0;
                    } else {
                        this.cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Run a task after player load
     */
    public BukkitTask runAfterPlayerLoad(Runnable task) {
        return runLater(task, Constants.Timing.HALF_SECOND);
    }

    /**
     * Run a cleanup task
     */
    public BukkitTask runCleanup(Runnable task) {
        return runLater(task, Constants.Animation.TAP_CLEANUP_DELAY);
    }

    /**
     * Run a task with retry logic
     */
    public BukkitTask runWithRetry(Runnable task, int maxAttempts, long delayBetweenAttempts) {
        return new BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                try {
                    task.run();
                    this.cancel(); // Success, cancel retry
                } catch (Exception e) {
                    attempts++;
                    if (attempts >= maxAttempts) {
                        this.cancel(); // Max attempts reached
                    }
                }
            }
        }.runTaskTimer(plugin, 0, delayBetweenAttempts);
    }

    /**
     * Run a task with timeout
     */
    public BukkitTask runWithTimeout(Runnable task, long timeoutTicks, Consumer<Exception> onTimeout) {
        BukkitTask timeoutTask = runLater(() -> {
            onTimeout.accept(new RuntimeException("Task timed out"));
        }, timeoutTicks);

        return new BukkitRunnable() {
            @Override
            public void run() {
                timeoutTask.cancel();
                task.run();
                this.cancel();
            }
        }.runTask(plugin);
    }

    /**
     * Cancel all tasks managed by this scheduler
     */
    public void cancelAll() {
        namedTasks.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        namedTasks.clear();

        playerTasks.values().forEach(tasks -> {
            tasks.values().forEach(task -> {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            });
        });
        playerTasks.clear();
    }

    /**
     * Get the number of active tasks
     */
    public int getActiveTaskCount() {
        int count = (int) namedTasks.values().stream()
                .filter(task -> !task.isCancelled())
                .count();

        count += playerTasks.values().stream()
                .flatMap(tasks -> tasks.values().stream())
                .filter(task -> !task.isCancelled())
                .count();

        return count;
    }

    /**
     * Clean up tasks for offline players
     */
    public void cleanupOfflinePlayers(java.util.Set<UUID> onlinePlayers) {
        playerTasks.keySet().removeIf(playerId -> {
            if (!onlinePlayers.contains(playerId)) {
                cancelAllPlayerTasks(playerId);
                return true;
            }
            return false;
        });
    }
}

