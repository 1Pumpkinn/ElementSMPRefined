package net.rose.elementSMPRefined.ability.passive.basic.water.listeners;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ElementSMPRefined;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.visual.PassiveInvisibility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WaterInvisibilityListener implements Listener {

    private static final long MONITOR_PERIOD_TICKS = 4L;
    private static final long BLANK_PERIOD_TICKS = 2L;
    private static final int IDLE_SECONDS_REQUIRED = 10;
    private static final int IDLE_CHECKS_REQUIRED = (int) (IDLE_SECONDS_REQUIRED * 20L / MONITOR_PERIOD_TICKS);

    // "Standing still" is judged per monitor period. Water constantly pushes a motionless player
    // (they sink ~0.1 blocks per period and bob at the surface), so a plain 3D distance check
    // never saw anyone as idle unless they were resting on the bottom. Instead: almost no sideways
    // movement, and vertical movement only counts if it is more than passive sinking / bobbing.
    private static final double HORIZONTAL_THRESHOLD_SQUARED = 0.0036;
    private static final double MAX_SINK_PER_PERIOD = -0.25;
    private static final double MAX_RISE_PER_PERIOD = 0.1;

    private static final EquipmentSlot[] VISIBLE_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND
    };

    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    // Stored on the player (survives restarts/crashes/hot-swaps) so a leftover infinite invisibility
    // from this passive can be recognised and removed later.
    private final NamespacedKey markerKey;

    private final Map<UUID, Integer> idleChecks = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.Location> lastLocation = new ConcurrentHashMap<>();
    private final Set<UUID> trulyInvisible = ConcurrentHashMap.newKeySet();

    private BukkitTask monitorTask;
    private BukkitTask blankTask;

    public WaterInvisibilityListener(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.markerKey = new NamespacedKey(plugin, "water_invisible");
        startMonitor();
        startBlankTask();
        // Plugin (re)enabled with players already online: clear any invisibility left by a previous run.
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::clearStaleInvisibility));
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

    private void startBlankTask() {
        // Observers who walk into range, teleport or respawn get the real gear from vanilla's spawn
        // packet, so concealed players are blanked again every couple of ticks. They are never hidden
        // from the tab list or the spectator teleport menu, only their gear is blanked.
        blankTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (trulyInvisible.isEmpty()) return;
            for (UUID id : trulyInvisible) {
                Player subject = Bukkit.getPlayer(id);
                if (subject != null && subject.isOnline()) {
                    sendEquipment(subject, blankEquipment());
                }
            }
        }, 20L, BLANK_PERIOD_TICKS);
    }

    private boolean hasPassive(Player player) {
        ElementType type = elementManager.getPlayerElement(player);
        if (type != ElementType.WATER && type != ElementType.FROST) return false;

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
                // Re-blank every period so observers who just started tracking them don't see real gear.
                sendEquipment(player, blankEquipment());
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
        player.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        // Deliberately NOT hideEntity: a hidden entity can't be targeted by other players' clients, so
        // nobody could ever hit them. Invisibility + blank equipment keeps them unseen but hittable.
        sendEquipment(player, blankEquipment());
    }

    private void revealPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        trulyInvisible.remove(uuid);
        PassiveInvisibility.unmark(player);
        removeOwnInvisibility(player);
        showPlayerToAll(player);
    }

    /** Removes the passive's infinite invisibility and the persistent marker. */
    private void removeOwnInvisibility(Player player) {
        player.getPersistentDataContainer().remove(markerKey);
        PotionEffect current = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (current != null && (current.getDuration() > 1000000 || current.getDuration() == PotionEffect.INFINITE_DURATION)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    /**
     * Clears invisibility this passive left behind in an earlier run (server stop/crash, plugin reload
     * or hot-swap) - nothing tracks it any more, so without this it would last forever. Recognised by
     * the persistent marker, or - for players stuck from before the marker existed - by the exact
     * signature of this passive's effect on a Water/Frost player.
     */
    private void clearStaleInvisibility(Player player) {
        if (!player.isOnline() || trulyInvisible.contains(player.getUniqueId())) return;

        boolean marked = player.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
        boolean legacy = false;
        if (!marked) {
            ElementType type = elementManager.getPlayerElement(player);
            PotionEffect effect = player.getPotionEffect(PotionEffectType.INVISIBILITY);
            legacy = (type == ElementType.WATER || type == ElementType.FROST)
                    && effect != null
                    && effect.getDuration() == PotionEffect.INFINITE_DURATION
                    && !effect.isAmbient() && !effect.hasParticles();
        }
        if (!marked && !legacy) return;

        PassiveInvisibility.unmark(player);
        removeOwnInvisibility(player);
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
        // Put the real armor / held items back for anyone who was sent the blank set.
        sendEquipment(subject, realEquipment(subject));
    }

    @EventHandler
    public void onEquipmentChange(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (trulyInvisible.contains(player.getUniqueId())) {
            sendEquipment(player, blankEquipment());
            // Vanilla broadcasts the new item right after this event, so blank it again a tick later.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && trulyInvisible.contains(player.getUniqueId())) {
                    sendEquipment(player, blankEquipment());
                }
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player observer = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!observer.isOnline()) return;
            clearStaleInvisibility(observer); // left over from a crash / unclean shutdown
            for (UUID id : trulyInvisible) {
                Player subject = Bukkit.getPlayer(id);
                if (subject != null && !subject.equals(observer)) {
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
            removeOwnInvisibility(player);
            if (player.isOnline()) {
                showPlayerToAll(player);
            }
        }
    }

    public void cleanup() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        if (blankTask != null) {
            blankTask.cancel();
        }
        // Server stop / plugin disable: actually take the invisibility off everyone, otherwise the
        // infinite potion effect is saved with the player and never ends.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (trulyInvisible.contains(player.getUniqueId())) {
                revealPlayer(player);
            }
        }
        trulyInvisible.forEach(PassiveInvisibility::unmark);
        trulyInvisible.clear();
        idleChecks.clear();
        lastLocation.clear();
    }
}