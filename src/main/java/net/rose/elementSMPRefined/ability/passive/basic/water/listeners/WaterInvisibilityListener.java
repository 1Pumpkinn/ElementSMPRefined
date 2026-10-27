package net.rose.elementSMPRefined.ability.passive.basic.water.listeners;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ElementSMPRefined;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.visual.PassiveInvisibility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water passive 2 (unlocks at Upgrade II): standing still in water grants true invisibility from other players.
 * Frost inherits this passive from Water, so it applies to both elements.
 * This is implemented with Bukkit hide/show semantics so the player remains fully visible
 * to themselves while all other players cannot see their armor, held items, or body.
 */
public class WaterInvisibilityListener implements Listener {

    private static final int IDLE_CHECKS_REQUIRED = 8;
    private static final long MONITOR_PERIOD_TICKS = 4L;

    // "Standing still" is judged per monitor period. Water constantly pushes a motionless player
    // (they sink ~0.1 blocks per period and bob at the surface), so a plain 3D distance check
    // never saw anyone as idle unless they were resting on the bottom. Instead: almost no sideways
    // movement, and vertical movement only counts if it is more than passive sinking / bobbing.
    /** Max sideways travel per period (squared, blocks) that still counts as standing still. */
    private static final double HORIZONTAL_THRESHOLD_SQUARED = 0.0036;
    /** Most a motionless player can sink per period (blocks, negative = down). */
    private static final double MAX_SINK_PER_PERIOD = -0.25;
    /** Most a motionless player can rise per period; holding jump / swimming up is faster than this. */
    private static final double MAX_RISE_PER_PERIOD = 0.1;

    /** Every visible equipment slot: armor, main hand, off hand. */
    private static final EquipmentSlot[] VISIBLE_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND
    };

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
        if (type != ElementType.WATER && type != ElementType.FROST) return false;

        // Second passive: unlocks at Upgrade II
        var data = elementManager.data(player.getUniqueId());
        return data != null && data.getUpgradeLevel(type) >= BaseElement.PASSIVE_2_UPGRADE_LEVEL;
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
        // getLocation() already returns a fresh copy, so it can be stored as is.
        org.bukkit.Location previous = lastLocation.put(uuid, current);

        boolean stationary = previous != null && isStationary(previous, current);

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

    private static boolean isStationary(org.bukkit.Location previous, org.bukkit.Location current) {
        if (previous.getWorld() == null || !previous.getWorld().equals(current.getWorld())) {
            return false;
        }
        double dx = current.getX() - previous.getX();
        double dz = current.getZ() - previous.getZ();
        double dy = current.getY() - previous.getY();
        return dx * dx + dz * dz < HORIZONTAL_THRESHOLD_SQUARED
                && dy > MAX_SINK_PER_PERIOD
                && dy < MAX_RISE_PER_PERIOD;
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
            hideFrom(observer, subject);
        }
        // Belt and braces: the potion effect alone leaves armor and held items visible, so if the
        // entity is ever still tracked by an observer (hide not applied yet, another plugin re-showing
        // it...) it must at least be bare. Harmless for observers the entity is already hidden from.
        sendEquipment(subject, blankEquipment());
    }

    /**
     * hideEntity (not hidePlayer): the subject stays in the observer's tab list, and there is no
     * listPlayer call - listPlayer throws "Player cannot see other player" once the subject is hidden,
     * which used to abort the whole hide loop every tick.
     */
    private void hideFrom(Player observer, Player subject) {
        try {
            observer.hideEntity(plugin, subject);
        } catch (RuntimeException ignored) {
            // never let one observer break the others
        }
    }

    private static Map<EquipmentSlot, ItemStack> blankEquipment() {
        Map<EquipmentSlot, ItemStack> blank = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : VISIBLE_SLOTS) {
            blank.put(slot, new ItemStack(Material.AIR));
        }
        return blank;
    }

    private static Map<EquipmentSlot, ItemStack> realEquipment(Player subject) {
        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : VISIBLE_SLOTS) {
            ItemStack item = subject.getInventory().getItem(slot);
            real.put(slot, item == null ? new ItemStack(Material.AIR) : item);
        }
        return real;
    }

    private void sendEquipment(Player subject, Map<EquipmentSlot, ItemStack> items) {
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(subject)) {
                continue;
            }
            observer.sendEquipmentChange(subject, items);
        }
    }

    private void showPlayerToAll(Player subject) {
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(subject)) {
                continue;
            }
            try {
                observer.showEntity(plugin, subject);
            } catch (RuntimeException ignored) {
                // keep going for the remaining observers
            }
        }
        // Put the real armor / held items back for anyone who was sent the blank set.
        sendEquipment(subject, realEquipment(subject));
    }

    @EventHandler
    public void onEquipmentChange(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (trulyInvisible.contains(player.getUniqueId())) {
            hidePlayerFromAll(player);
            // Vanilla broadcasts the new item right after this event, so blank it again a tick later.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && trulyInvisible.contains(player.getUniqueId())) {
                    sendEquipment(player, blankEquipment());
                }
            });
        }
    }

    /** Someone who joins while a player is invisible must not see them for even a few ticks. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player observer = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!observer.isOnline()) return;
            for (UUID id : trulyInvisible) {
                Player subject = Bukkit.getPlayer(id);
                if (subject != null && !subject.equals(observer)) {
                    hideFrom(observer, subject);
                    observer.sendEquipmentChange(subject, blankEquipment());
                }
            }
        });
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