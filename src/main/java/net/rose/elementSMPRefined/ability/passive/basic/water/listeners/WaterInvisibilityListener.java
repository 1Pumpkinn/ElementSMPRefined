package net.rose.elementSMPRefined.ability.passive.basic.water.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ElementSMPRefined;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.visual.PassiveInvisibility;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water passive 2: standing still in water grants true invisibility from other players.
 * Frost inherits this passive from Water, so it applies to both elements.
 * This is implemented with Bukkit hide/show semantics so the player remains fully visible
 * to themselves while all other players cannot see their armor, held items, or body.
 */
public class WaterInvisibilityListener implements Listener {

    private static final int IDLE_CHECKS_REQUIRED = 8;
    private static final double MOVE_THRESHOLD_SQUARED = 0.0009;
    private static final long MONITOR_PERIOD_TICKS = 4L;

    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    private final Map<UUID, Integer> idleChecks = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.Location> lastLocation = new ConcurrentHashMap<>();
    private final Set<UUID> trulyInvisible = ConcurrentHashMap.newKeySet();

    private BukkitTask monitorTask;

    public WaterInvisibilityListener(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        startMonitor();
    }

    private void startMonitor() {
        monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Only check players whose element has this passive to avoid wasting cycles on other elements
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (hasPassive(player)) {
                    tick(player);
                } else if (trulyInvisible.contains(player.getUniqueId())) {
                    // No longer Water/Frost (reroll/admin set) - tick() never runs for them again, so reveal here.
                    idleChecks.remove(player.getUniqueId());
                    lastLocation.remove(player.getUniqueId());
                    revealPlayer(player);
                }
            }
        }, 20L, MONITOR_PERIOD_TICKS);
    }

    /** Water and Frost (upgraded from Water) both get the in-water invisibility passive. */
    private boolean hasPassive(Player player) {
        ElementType type = elementManager.getPlayerElement(player);
        return type == ElementType.WATER || type == ElementType.FROST;
    }

    private void tick(Player player) {
        UUID uuid = player.getUniqueId();

        if (!player.isInWater()) {
            idleChecks.remove(uuid);
            if (trulyInvisible.contains(uuid)) {
                revealPlayer(player);
            }
            return;
        }

        org.bukkit.Location current = player.getLocation();
        org.bukkit.Location previous = lastLocation.put(uuid, current.clone());

        boolean stationary = previous != null
                && previous.getWorld().equals(current.getWorld())
                && previous.distanceSquared(current) < MOVE_THRESHOLD_SQUARED;

        if (stationary) {
            int checks = idleChecks.merge(uuid, 1, Integer::sum);
            if (checks >= IDLE_CHECKS_REQUIRED && !trulyInvisible.contains(uuid)) {
                concealPlayer(player);
            } else if (trulyInvisible.contains(uuid)) {
                // Refresh visibility cache
                hidePlayerFromAll(player);
            }
        } else {
            idleChecks.remove(uuid);
            if (trulyInvisible.contains(uuid)) {
                revealPlayer(player);
            }
        }
    }

    private void concealPlayer(Player player) {
        trulyInvisible.add(player.getUniqueId());
        PassiveInvisibility.mark(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        hidePlayerFromAll(player);
    }

    private void revealPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        trulyInvisible.remove(uuid);
        PassiveInvisibility.unmark(player);

        PotionEffect current = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (current != null && (current.getDuration() > 1000000 || current.getDuration() == PotionEffect.INFINITE_DURATION)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        showPlayerToAll(player);
    }

    private void hidePlayerFromAll(Player subject) {
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(subject)) {
                continue;
            }
            observer.hidePlayer(plugin, subject);
            // hidePlayer also drops the subject from the observer's tab list;
            // put them back so they still show in the tablist while invisible.
            observer.listPlayer(subject);
        }
    }

    private void showPlayerToAll(Player subject) {
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(subject)) {
                continue;
            }
            observer.showPlayer(plugin, subject);
        }
    }

    @EventHandler
    public void onEquipmentChange(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (trulyInvisible.contains(player.getUniqueId())) {
            hidePlayerFromAll(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        idleChecks.remove(uuid);
        lastLocation.remove(uuid);
        if (trulyInvisible.remove(uuid)) {
            PassiveInvisibility.unmark(player);
            PotionEffect current = player.getPotionEffect(PotionEffectType.INVISIBILITY);
            if (current != null && (current.getDuration() > 1000000 || current.getDuration() == PotionEffect.INFINITE_DURATION)) {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
            if (player.isOnline()) {
                showPlayerToAll(player);
            }
        }
    }

    public void cleanup() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (trulyInvisible.contains(player.getUniqueId())) {
                showPlayerToAll(player);
            }
        }
        trulyInvisible.forEach(PassiveInvisibility::unmark);
        trulyInvisible.clear();
        idleChecks.clear();
        lastLocation.clear();
    }
}