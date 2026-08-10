package hs.elementSMPRefined.util.scheduling;

import hs.elementPlugin.ElementPlugin;
import hs.elementPlugin.config.Constants;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class TaskScheduler {
    private final ElementPlugin plugin;

    public TaskScheduler(ElementPlugin plugin) {
        this.plugin = plugin;
    }

    public BukkitTask runLater(Runnable task, long delayTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskLater(plugin, delayTicks);
    }

    public BukkitTask runLaterSeconds(Runnable task, int seconds) {
        return runLater(task, seconds * Constants.Timing.TICKS_PER_SECOND);
    }

    public BukkitTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskTimer(plugin, delayTicks, periodTicks);
    }

    public BukkitTask runTimerSeconds(Runnable task, int delaySeconds, int periodSeconds) {
        return runTimer(task,
                delaySeconds * Constants.Timing.TICKS_PER_SECOND,
                periodSeconds * Constants.Timing.TICKS_PER_SECOND
        );
    }

    public BukkitTask runAfterPlayerLoad(Runnable task) {
        return runLater(task, Constants.Timing.HALF_SECOND);
    }

    public BukkitTask runCleanup(Runnable task) {
        return runLater(task, Constants.Animation.TAP_CLEANUP_DELAY);
    }
}

