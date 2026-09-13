package net.rose.elementSMPRefined.listeners.ability;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.config.Constants;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.status.DisarmManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AbilityListener implements Listener {
    private static final Component ABILITY_DISARMED = Component.text(
            "You are disarmed and cannot use abilities!", NamedTextColor.RED);

    private final ElementSMPRefined plugin;
    private final ElementManager elements;
    private final DisarmManager disarmManager;
    // Plain HashMap, not ConcurrentHashMap: every access point (this event
    // handler, the two BukkitRunnable callbacks below via runTaskLater, and
    // PlayerLifecycle's onPlayerQuit call to onPlayerQuit(UUID)) runs
    // synchronously on the main server thread - nothing here ever touches
    // this map off-thread, so the concurrency bookkeeping was pure overhead
    // on a per-tap hot path.
    private final Map<UUID, TapTracker> tapTrackers = new HashMap<>();

    public AbilityListener(ElementSMPRefined plugin, ElementManager elements, DisarmManager disarmManager) {
        this.plugin = plugin;
        this.elements = elements;
        this.disarmManager = disarmManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        if (!hasElement(player)) return;

        UUID playerId = player.getUniqueId();
        TapTracker tracker = tapTrackers.computeIfAbsent(playerId, k -> new TapTracker());

        long currentTime = System.currentTimeMillis();

        if (tracker.isDoubleTap(currentTime)) {
            tracker.reset();
            scheduleCleanup(playerId);
            return;
        }

        event.setCancelled(true);
        tracker.recordTap(currentTime, player.isSneaking());

        scheduleAbilityActivation(player, playerId, currentTime);
    }

    private boolean hasElement(Player player) {
        PlayerData pd = elements.data(player.getUniqueId());
        return pd.getCurrentElementId() != null;
    }

    private void scheduleAbilityActivation(Player player, UUID playerId, long tapTime) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    tapTrackers.remove(playerId);
                    return;
                }

                TapTracker tracker = tapTrackers.get(playerId);
                if (tracker == null || !tracker.isValidTap(tapTime)) {
                    return;
                }

                if (disarmManager.isAbilityDisarmed(player)) {
                    player.sendActionBar(ABILITY_DISARMED);
                    return;
                }

                boolean success = tracker.wasShiftHeld ?
                        elements.useAbility2(player) :
                        elements.useAbility1(player);

                if (success) {
                    scheduleCleanup(playerId);
                }
            }
        }.runTaskLater(plugin, Constants.Animation.TAP_CHECK_DELAY);
    }

    private void scheduleCleanup(UUID playerId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                tapTrackers.remove(playerId);
            }
        }.runTaskLater(plugin, Constants.Animation.TAP_CLEANUP_DELAY);
    }

    private static class TapTracker {
        private long lastTapTime = 0;
        private boolean wasShiftHeld = false;

        boolean isDoubleTap(long currentTime) {
            return lastTapTime > 0 && (currentTime - lastTapTime) <= Constants.Animation.DOUBLE_TAP_THRESHOLD_MS;
        }

        void recordTap(long time, boolean shiftHeld) {
            this.lastTapTime = time;
            this.wasShiftHeld = shiftHeld;
        }

        boolean isValidTap(long originalTime) {
            return lastTapTime == originalTime;
        }

        void reset() {
            lastTapTime = 0;
            wasShiftHeld = false;
        }
    }

    public void onPlayerQuit(UUID playerUuid) {
        tapTrackers.remove(playerUuid);
    }
}