package net.rose.elementSMPRefined.util.server;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.scheduling.TaskScheduler;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grace period: a countdown (boss bar optional) shown to everyone while active,
 * during which players are kept fed for a shorter opening window (by giving
 * them the Saturation effect, then taking it away again) and PvP can be
 * blocked for the whole thing. Which protections apply, the boss bar
 * look, the announcements and what happens when it ends are all config.yml
 * options under {@code grace_period} (also editable with {@code /grace set}).
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
 * <p>
 * <b>Saturation bookkeeping:</b> potion effects are saved with the player, so
 * an effect we hand out could outlive the grace period (quit mid-grace, server
 * crash). Every effect we give is tagged with a marker in the player's
 * persistent data; we only ever remove effects that carry that marker (so a
 * real Saturation potion is never touched), we strip it on quit, when the
 * hunger window or grace period ends, on plugin disable, and on join if it's
 * no longer supposed to be there.
 */
public class GracePeriod implements Listener {
    private final ElementSMPRefined plugin;
    private final ConfigManager configManager;
    private final TaskScheduler scheduler;

    private final Map<UUID, Long> pvpWarningCooldowns = new HashMap<>();
    private final NamespacedKey saturationMarkerKey;

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
        this.saturationMarkerKey = new NamespacedKey(plugin, "grace_saturation");
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

        if (configManager.isGracePeriodBossBarEnabled()) {
            this.bossBar = BossBar.bossBar(
                    Lang.gracePeriodBossBarTitle(ParseUtil.formatClock(remainingSeconds)),
                    1.0f,
                    configManager.getGracePeriodBossBarColor(),
                    configManager.getGracePeriodBossBarStyle()
            );
            plugin.getServer().getOnlinePlayers().forEach(player -> player.showBossBar(bossBar));
        }

        if (configManager.isGracePeriodBroadcastStart()) {
            boolean hungerWindowUsed = configManager.isGracePeriodHungerProtection() && this.hungerProtectionSeconds > 0;
            plugin.getServer().broadcast(Lang.gracePeriodStarted(
                    ParseUtil.formatCompact(totalDurationSeconds),
                    configManager.isGracePeriodPvpProtection(),
                    hungerWindowUsed ? ParseUtil.formatCompact(this.hungerProtectionSeconds) : null
            ));
        }

        this.task = scheduler.runTimer(this::tick, Constants.Timing.TICKS_PER_SECOND, Constants.Timing.TICKS_PER_SECOND);
        syncSaturation();
        return true;
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
        // Hands out / refreshes the effect while protected, and takes it away
        // the moment the hunger window (or the hunger toggle) says it's over.
        syncSaturation();
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
        syncSaturation();

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

        if (configManager.isGracePeriodBroadcastEnd()) {
            plugin.getServer().broadcast(Lang.gracePeriodEnded(configManager.isGracePeriodPvpProtection()));
        }

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
        syncSaturation();
    }

    public boolean isPvpDisabled() {
        return active && configManager.isGracePeriodPvpProtection();
    }

    public boolean isHungerProtected() {
        if (!active || !configManager.isGracePeriodHungerProtection()) {
            return false;
        }
        int elapsedSeconds = totalDurationSeconds - remainingSeconds;
        return elapsedSeconds < hungerProtectionSeconds;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (active && bossBar != null) {
            player.showBossBar(bossBar);
        }

        // Anything we left on them from a previous session (crash, forced
        // shutdown) is stale: strip it, then hand out a fresh, correctly
        // timed one only if hunger protection is genuinely still running.
        removeSaturation(player);
        if (isHungerProtected()) {
            applySaturation(player);
        }
    }

    /** Drops a quitting player's cooldown entry, boss bar viewer state and any saturation we gave them instead of letting any of it linger. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        pvpWarningCooldowns.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
        removeSaturation(player);
    }

    /** Death wipes all potion effects, so our tag on the player no longer describes anything real; clear it too. */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.getEntity().getPersistentDataContainer().remove(saturationMarkerKey);
    }

    /**
     * Brings every online player in line with the current state: while hunger
     * protection is running they get the Saturation effect (re-applied if it
     * was lost, e.g. milk bucket or respawn); otherwise anything we gave out
     * is removed.
     */
    private void syncSaturation() {
        boolean protectedNow = isHungerProtected();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (protectedNow) {
                applySaturation(player);
            } else {
                removeSaturation(player);
            }
        }
    }

    /** Gives the tagged Saturation effect lasting exactly the rest of the hunger window, unless they already have one. */
    private void applySaturation(Player player) {
        if (player.hasPotionEffect(PotionEffectType.SATURATION)) {
            return;
        }
        int remainingProtectionSeconds = hungerProtectionSeconds - (totalDurationSeconds - remainingSeconds);
        if (remainingProtectionSeconds <= 0) {
            return;
        }
        // A short buffer keeps the effect from lapsing a hair before our own
        // once-a-second removal; the removal is what actually ends it.
        int durationTicks = remainingProtectionSeconds * 20 + 40;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, durationTicks, 255, false, false, true));
        player.getPersistentDataContainer().set(saturationMarkerKey, PersistentDataType.BYTE, (byte) 1);
    }

    /** Removes the Saturation effect only if we were the ones who gave it. A real potion/golden food effect is left alone. */
    private void removeSaturation(Player player) {
        if (!player.getPersistentDataContainer().has(saturationMarkerKey, PersistentDataType.BYTE)) {
            return;
        }
        player.removePotionEffect(PotionEffectType.SATURATION);
        player.getPersistentDataContainer().remove(saturationMarkerKey);
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