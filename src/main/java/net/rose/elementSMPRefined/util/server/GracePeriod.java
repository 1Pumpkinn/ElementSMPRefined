package net.rose.elementSMPRefined.util.server;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.config.Constants;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.scheduling.TaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grace period: a countdown boss bar shown to everyone while active, during
 * which hunger loss is blocked for a shorter opening window and PvP is
 * blocked for the whole thing.
 * <p>
 * Started/stopped with {@link #start(int, int)} / {@link #stop()}, normally
 * from {@code /grace start|stop} (see {@link net.rose.elementSMPRefined.commands}) -
 * this class does nothing on its own unless something calls start(). The one
 * exception is {@code grace_period.auto_start: true} in config.yml, which
 * {@link net.rose.elementSMPRefined.core.initializers.ListenerInitializer}
 * uses to call {@link #start(int, int)} once on plugin enable, for servers
 * that want the old "starts with the server" behaviour instead of a command.
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
    private int hungerProtectionSeconds;
    private int remainingSeconds;
    private boolean active;

    public GracePeriod(ElementSMPRefined plugin, ConfigManager configManager, TaskScheduler scheduler) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
    }

    /** Reads config.yml's grace_period.auto_start and, if set, starts the grace period with the configured defaults. Called once on plugin enable. */
    public void autoStartIfConfigured() {
        if (configManager.isGracePeriodAutoStart()) {
            start(configManager.getGracePeriodDurationSeconds(), configManager.getGracePeriodHungerProtectionSeconds());
        }
    }

    /**
     * Starts the grace period with the given durations (both in seconds).
     * Returns false (no-op) if one is already running - call {@link #stop()}
     * first if you want to restart it with different values.
     */
    public boolean start(int durationSeconds, int hungerProtectionSeconds) {
        if (active) {
            return false;
        }

        this.totalDurationSeconds = Math.max(1, durationSeconds);
        this.hungerProtectionSeconds = Math.max(0, Math.min(hungerProtectionSeconds, this.totalDurationSeconds));
        this.remainingSeconds = totalDurationSeconds;
        this.active = true;

        this.bossBar = BossBar.bossBar(
                Lang.gracePeriodBossBarTitle(formatTime(remainingSeconds)),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );

        plugin.getServer().getOnlinePlayers().forEach(player -> player.showBossBar(bossBar));
        plugin.getServer().broadcast(Lang.GRACE_PERIOD_STARTED);

        this.task = scheduler.runTimer(this::tick, Constants.Timing.TICKS_PER_SECOND, Constants.Timing.TICKS_PER_SECOND);
        return true;
    }

    /** Ends the grace period early. Returns false (no-op) if it wasn't running. */
    public boolean stop() {
        if (!active) {
            return false;
        }
        end();
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
            end();
            return;
        }

        bossBar.name(Lang.gracePeriodBossBarTitle(formatTime(remainingSeconds)));
        bossBar.progress((float) remainingSeconds / totalDurationSeconds);
    }

    private void end() {
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

    public boolean isPvpDisabled() {
        return active;
    }

    public boolean isHungerProtected() {
        if (!active) {
            return false;
        }
        int elapsedSeconds = totalDurationSeconds - remainingSeconds;
        return elapsedSeconds < hungerProtectionSeconds;
    }

    private static String formatTime(int seconds) {
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    // --- Listeners ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (active && bossBar != null) {
            event.getPlayer().showBossBar(bossBar);
        }
    }

    /** Drops a quitting player's cooldown entry and boss bar viewer state instead of letting either linger. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pvpWarningCooldowns.remove(event.getPlayer().getUniqueId());
        if (bossBar != null) {
            event.getPlayer().hideBossBar(bossBar);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!isHungerProtected()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // Only block a drop in hunger - still let hunger increase (e.g. eating).
        if (event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
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