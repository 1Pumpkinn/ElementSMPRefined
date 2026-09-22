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
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-start grace period: a countdown boss bar is shown to everyone while
 * it's active, during which hunger loss is blocked for a shorter opening
 * window and PvP is blocked for the whole thing. Durations are read from
 * {@link ConfigManager} (grace_period.* in config.yml) rather than hardcoded,
 * so they can be tuned without touching Java.
 * <p>
 * {@link #start()} is called once, from {@link net.rose.elementSMPRefined.core.initializers.ListenerInitializer}
 * right after this listener is registered. {@link #cleanup()} is called on
 * plugin disable so a lingering boss bar/task doesn't survive a reload.
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

    /** Begins the grace period, if enabled in config. Safe to call exactly once, on plugin enable. */
    public void start() {
        if (!configManager.isGracePeriodEnabled()) {
            return;
        }

        this.totalDurationSeconds = Math.max(1, configManager.getGracePeriodDurationSeconds());
        this.hungerProtectionSeconds = Math.max(0, configManager.getGracePeriodHungerProtectionSeconds());
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
        }

        plugin.getServer().broadcast(Lang.GRACE_PERIOD_ENDED);
    }

    /** Cancels the countdown and clears the boss bar early, e.g. on plugin disable. Idempotent. */
    public void cleanup() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        if (active && bossBar != null) {
            plugin.getServer().getOnlinePlayers().forEach(player -> player.hideBossBar(bossBar));
        }

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
        if (lastWarned != null && now - lastWarned < Constants.GracePeriod.PVP_WARNING_COOLDOWN_MS) {
            return;
        }
        pvpWarningCooldowns.put(damager.getUniqueId(), now);
        damager.sendMessage(Lang.GRACE_PERIOD_PVP_DISABLED);
    }
}