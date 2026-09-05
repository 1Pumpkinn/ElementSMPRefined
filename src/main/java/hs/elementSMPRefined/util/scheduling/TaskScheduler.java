package hs.elementSMPRefined.util.scheduling;

import hs.elementSMPRefined.config.Constants;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Thin wrapper around Bukkit's scheduler for the plugin's basic task-scheduling
 * needs (sync/async, delayed, repeating).
 * <p>
 * There used to be a lot more on this class - named task registries, per-player
 * task tracking/cancellation, retry/timeout/sequence helpers - none of which
 * were ever actually called anywhere in the plugin. That surface was deleted
 * rather than kept "just in case"; add it back deliberately, with a real call
 * site, if a future feature needs it. Only the methods with an actual caller
 * (or that are trivial one-line Bukkit wrappers other code is likely to reach
 * for next) were kept.
 * <p>
 * Always use {@code plugin.getTaskScheduler()} rather than constructing a new
 * instance - there was previously a bug where a second, unrelated instance got
 * created in {@code PlayerLifecycle}, which is exactly the kind of bug this
 * class exists to avoid if there's ever a reason to track tasks by name again.
 */
public final class TaskScheduler {
    private final JavaPlugin plugin;

    public TaskScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Run a task immediately on the main thread. */
    public BukkitTask runNow(Runnable task) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTask(plugin);
    }

    /** Run a task later on the main thread. */
    public BukkitTask runLater(Runnable task, long delayTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskLater(plugin, delayTicks);
    }

    /** Run a task later on the main thread, with the delay given in seconds. */
    public BukkitTask runLaterSeconds(Runnable task, int seconds) {
        return runLater(task, seconds * Constants.Timing.TICKS_PER_SECOND);
    }

    /** Run a repeating task on the main thread. */
    public BukkitTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskTimer(plugin, delayTicks, periodTicks);
    }

    /** Run a task asynchronously (off the main thread). */
    public BukkitTask runAsync(Runnable task) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Run a task shortly after a player-related event, once their client-side
     * state has had a moment to settle (join, respawn, totem pop, etc).
     */
    public BukkitTask runAfterPlayerLoad(Runnable task) {
        return runLater(task, Constants.Timing.HALF_SECOND);
    }
}
