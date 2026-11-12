package net.rose.elementevents.api;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementevents.api.event.ServerEventEndEvent;
import net.rose.elementevents.api.event.ServerEventStartEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * A {@link ServerEvent} that runs for a fixed number of seconds with a progress boss bar.
 * Handles everything that is the same for every timed event - the active flag, the countdown
 * task, the boss bar, late joiners, config-backed default duration and the start/end Bukkit
 * events - so a concrete event only implements what is special about it through the {@code on...}
 * hooks.
 * <p>
 * Lifecycle: {@link #start} -> {@link #onStart()} ... {@link #onTick(int)} each second ...
 * then either the timer runs out / {@link #end()} -> {@link #onEnd()}, or {@link #cancel()} ->
 * {@link #onCancel()}. By the time a hook runs the event is already inactive (except
 * {@code onStart}/{@code onTick}), the timer is stopped and the boss bar is hidden.
 */
public abstract class AbstractTimedEvent implements ServerEvent {
    protected final ElementSMPRefined plugin;
    private final String id;
    private final Component title;
    private final BossBar.Color barColor;
    private final int fallbackDurationSeconds;

    private boolean active;
    private int totalSeconds;
    private int remainingSeconds;
    private BossBar bossBar;
    private BukkitTask task;

    /**
     * @param id see {@link ServerEvent#getId()}; the config key {@code events.<id>.duration_seconds}
     *     overrides the default duration
     * @param fallbackDurationSeconds duration when the config has no value
     */
    protected AbstractTimedEvent(
            ElementSMPRefined plugin,
            String id,
            Component title,
            BossBar.Color barColor,
            int fallbackDurationSeconds) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        if (!EventIds.isValidId(id)) {
            throw new IllegalArgumentException(
                    "Event id must be lower-case words joined by underscores: " + id);
        }
        this.id = id;
        this.title = Objects.requireNonNull(title, "title");
        this.barColor = Objects.requireNonNull(barColor, "barColor");
        this.fallbackDurationSeconds = fallbackDurationSeconds;
    }

    // ---- hooks ------------------------------------------------------------------------------

    /** Reset state and announce the event. Called once the timer and boss bar are running. */
    protected abstract void onStart();

    /** Pick winners and hand out rewards. The event is already inactive. */
    protected abstract void onEnd();

    /** Clean up after a cancel; nothing is awarded. The event is already inactive. */
    protected abstract void onCancel();

    /** Called once per second while running, after the boss bar was updated. */
    protected void onTick(int remainingSeconds) {}

    // ---- ServerEvent ------------------------------------------------------------------------

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public final boolean isActive() {
        return active;
    }

    @Override
    public final int getRemainingSeconds() {
        return active ? remainingSeconds : 0;
    }

    /** The total length of the current run, or 0 when the event isn't running. */
    protected final int getTotalSeconds() {
        return active ? totalSeconds : 0;
    }

    @Override
    public int defaultDurationSeconds() {
        return Math.max(
                1,
                plugin.getConfig()
                        .getInt("events." + id + ".duration_seconds", fallbackDurationSeconds));
    }

    @Override
    public final boolean start(int durationSeconds) {
        if (active) return false;

        int seconds = Math.max(1, durationSeconds);
        ServerEventStartEvent startEvent = new ServerEventStartEvent(this, seconds);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) return false;

        active = true;
        totalSeconds = seconds;
        remainingSeconds = seconds;

        bossBar = BossBar.bossBar(title, 1.0f, barColor, BossBar.Overlay.PROGRESS);
        Bukkit.getOnlinePlayers().forEach(p -> p.showBossBar(bossBar));
        task =
                plugin.getTaskScheduler()
                        .runTimer(
                                this::tick,
                                Constants.Timing.TICKS_PER_SECOND,
                                Constants.Timing.TICKS_PER_SECOND);

        onStart();
        return true;
    }

    @Override
    public final boolean end() {
        if (!active) return false;
        shutdown();
        onEnd();
        Bukkit.getPluginManager()
                .callEvent(new ServerEventEndEvent(this, ServerEventEndEvent.Reason.COMPLETED));
        return true;
    }

    @Override
    public final boolean cancel() {
        if (!active) return false;
        shutdown();
        onCancel();
        Bukkit.getPluginManager()
                .callEvent(new ServerEventEndEvent(this, ServerEventEndEvent.Reason.CANCELLED));
        return true;
    }

    @Override
    public void cleanup() {
        shutdown();
    }

    @Override
    public void showTo(Player player) {
        if (active && bossBar != null) player.showBossBar(bossBar);
    }

    // ---- internals --------------------------------------------------------------------------

    /** Hides the boss bar and stops the timer. Idempotent. */
    private void shutdown() {
        active = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bossBar != null) {
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
            bossBar = null;
        }
    }

    private void tick() {
        remainingSeconds--;
        if (remainingSeconds <= 0) {
            end();
            return;
        }
        if (bossBar != null) {
            bossBar.progress(Math.max(0f, Math.min(1f, (float) remainingSeconds / totalSeconds)));
        }
        onTick(remainingSeconds);
    }
}
