package net.rose.elementSMPRefined.util.server;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.scheduling.TaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grace period: a countdown boss bar shown to everyone while active, during
 * which PvP is always blocked. On start it can disable the Nether and/or End
 * (config.yml {@code grace_period.on_start}). When it runs out on its own it can re-open the
 * Nether and/or End (config.yml {@code grace_period.on_end}).
 * <p>
 * Started/stopped with {@link #start(int)} / {@link #stop()}, from
 * {@code /grace start|stop}. It never starts by itself.
 * <p>
 * {@link #cleanup()} is called on plugin disable so a lingering boss
 * bar/task/listener state doesn't survive a reload.
 */
public class GracePeriod implements Listener {
    private final ElementSMPRefined plugin;
    private final ConfigManager configManager;
    private final TaskScheduler scheduler;

    private final Map<UUID, Long> pvpWarningCooldowns = new HashMap<>();

    private BossBar bossBar;
    private BukkitTask task;
    private int totalDurationSeconds;
    private int remainingSeconds;
    private boolean active;

    public GracePeriod(ElementSMPRefined plugin, ConfigManager configManager, TaskScheduler scheduler) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
    }

    /**
     * Starts the grace period with the given duration (in seconds).
     * Returns false (no-op) if one is already running - call {@link #stop()}
     * first if you want to restart it with different values.
     */
    public boolean start(int durationSeconds) {
        if (active) {
            return false;
        }

        this.totalDurationSeconds = Math.max(1, durationSeconds);
        this.remainingSeconds = totalDurationSeconds;
        this.active = true;

        this.bossBar = BossBar.bossBar(
                Lang.gracePeriodBossBarTitle(ParseUtil.formatClock(remainingSeconds)),
                1.0f,
                configManager.getGracePeriodBossBarColor(),
                BossBar.Overlay.PROGRESS
        );
        plugin.getServer().getOnlinePlayers().forEach(player -> player.showBossBar(bossBar));

        closeDimensionsOnStart();

        this.task = scheduler.runTimer(this::tick, Constants.Timing.TICKS_PER_SECOND, Constants.Timing.TICKS_PER_SECOND);
        return true;
    }

    /** Disables the dimensions listed under grace_period.on_start when the grace period begins. */
    private void closeDimensionsOnStart() {
        if (configManager.isGracePeriodDisableNetherOnStart() && !configManager.isNetherDisabled()) {
            configManager.setNetherDisabled(true);
            plugin.getServer().broadcast(Lang.dimensionCmdToggled("Nether", true));
        }
        if (configManager.isGracePeriodDisableEndOnStart() && !configManager.isEndDisabled()) {
            configManager.setEndDisabled(true);
            plugin.getServer().broadcast(Lang.dimensionCmdToggled("End", true));
        }
    }

    /** Ends the grace period early. Returns false (no-op) if it wasn't running. */
    public boolean stop() {
        if (!active) {
            return false;
        }
        end(false);
        return true;
    }

    /**
     * Adds (or, if negative, removes) time from the running grace period.
     * Returns false (no-op) if none is running, or if removing that much
     * would end it - use {@link #stop()} for that.
     */
    public boolean adjustTime(int deltaSeconds) {
        if (!active) {
            return false;
        }
        if (remainingSeconds + deltaSeconds <= 0) {
            return false;
        }
        remainingSeconds += deltaSeconds;
        totalDurationSeconds = Math.max(totalDurationSeconds + deltaSeconds, remainingSeconds);
        updateBossBar();
        return true;
    }

    public boolean isActive() {
        return active;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    private void tick() {
        remainingSeconds--;

        if (remainingSeconds <= 0) {
            end(true);
            return;
        }

        updateBossBar();
    }

    private void updateBossBar() {
        if (bossBar == null) {
            return;
        }
        bossBar.name(Lang.gracePeriodBossBarTitle(ParseUtil.formatClock(remainingSeconds)));
        bossBar.progress(Math.max(0f, Math.min(1f, (float) remainingSeconds / totalDurationSeconds)));
    }

    /** @param natural true when the timer ran out by itself, false when it was stopped early. */
    private void end(boolean natural) {
        active = false;

        if (task != null) {
            task.cancel();
            task = null;
        }

        if (bossBar != null) {
            plugin.getServer().getOnlinePlayers().forEach(player -> player.hideBossBar(bossBar));
            bossBar = null;
        }

        // Nothing left to rate-limit warnings for once PvP protection is over.
        pvpWarningCooldowns.clear();

        plugin.getServer().broadcast(Lang.GRACE_PERIOD_ENDED);

        if (natural) {
            openDimensionsOnEnd();
        }
    }

    /** Re-enables the dimensions listed under grace_period.on_end once the countdown finishes on its own. */
    private void openDimensionsOnEnd() {
        if (configManager.isGracePeriodEnableNetherOnEnd() && configManager.isNetherDisabled()) {
            configManager.setNetherDisabled(false);
            plugin.getServer().broadcast(Lang.dimensionCmdToggled("Nether", false));
        }
        if (configManager.isGracePeriodEnableEndOnEnd() && configManager.isEndDisabled()) {
            configManager.setEndDisabled(false);
            plugin.getServer().broadcast(Lang.dimensionCmdToggled("End", false));
        }
    }

    /** Cancels the countdown and clears the boss bar/state early, e.g. on plugin disable. Idempotent. */
    public void cleanup() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        if (bossBar != null) {
            plugin.getServer().getOnlinePlayers().forEach(player -> player.hideBossBar(bossBar));
            bossBar = null;
        }

        pvpWarningCooldowns.clear();
        active = false;
    }

    /** PvP is always off while the grace period is active. */
    public boolean isPvpDisabled() {
        return active;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (active && bossBar != null) {
            event.getPlayer().showBossBar(bossBar);
        }
    }

    /** Drops a quitting player's cooldown entry and boss bar viewer state instead of letting them linger. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        pvpWarningCooldowns.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isPvpDisabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player damager = extractDamager(event);
        if (damager == null || damager.equals(victim)) {
            return;
        }

        event.setCancelled(true);
        warnPvpDisabled(damager);
    }

    /** Extracts the player damager from a damage event, including projectile attacks. */
    private Player extractDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void warnPvpDisabled(Player damager) {
        long now = System.currentTimeMillis();
        Long lastWarned = pvpWarningCooldowns.get(damager.getUniqueId());
        if (lastWarned != null && now - lastWarned < Constants.Warnings.COOLDOWN_MS) {
            return;
        }
        pvpWarningCooldowns.put(damager.getUniqueId(), now);
        damager.sendMessage(Lang.GRACE_PERIOD_PVP_DISABLED);
    }
}