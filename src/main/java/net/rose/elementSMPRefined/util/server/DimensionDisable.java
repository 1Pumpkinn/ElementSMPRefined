package net.rose.elementSMPRefined.util.server;

import net.rose.elementSMPRefined.config.Constants;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cancels Nether/End portal travel, with each dimension toggled independently
 * via {@link ConfigManager#isNetherDisabled()} / {@link ConfigManager#isEndDisabled()}
 * (runtime-toggleable with /dimension, see {@link net.rose.elementSMPRefined.commands.DimensionCommand}).
 * <p>
 * Two events are handled because Bukkit fires a different one depending on
 * what enters the portal:
 * <ul>
 *   <li>{@link PlayerPortalEvent} - a player walking into a portal directly.
 *   {@link PlayerTeleportEvent#getCause()} tells us which dimension they were
 *   headed to.</li>
 *   <li>{@link EntityPortalEvent} - any other entity entering a portal (a
 *   vehicle a player is riding, a thrown ender pearl, a dropped item). This
 *   event has no cause, so the portal block itself at {@code getFrom()} is
 *   used to tell Nether portals from End portals/gateways.</li>
 * </ul>
 * <p>
 * <b>Why the pushback:</b> cancelling {@link PlayerPortalEvent} does not stop
 * the player from re-triggering it - vanilla's portal timer keeps counting up
 * while they stand in the portal block, so once it crosses the threshold
 * again the event fires again, every tick, for as long as they stay put. Left
 * alone that's a constant stream of cancelled events and repeated chat
 * messages, which is exactly what happens while testing this feature (stand
 * in the portal to see it get blocked). {@link #pushOutOfPortal(Player)}
 * shoves the player back the way they came so they physically leave the
 * portal block and the loop stops; {@link #warnDimensionDisabled(Player)}
 * also rate-limits the message itself as a second line of defense.
 */
public class DimensionDisable implements Listener {
    private final ConfigManager configManager;
    private final Map<UUID, Long> warningCooldowns = new HashMap<>();

    public DimensionDisable(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!isDisabled(fromCause(event.getCause()))) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        pushOutOfPortal(player);
        warnDimensionDisabled(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Material portalBlock = event.getFrom().getBlock().getType();
        Dimension target = fromBlock(portalBlock);

        // Covers vehicles (boats, horses, minecarts) with or without a
        // passenger, thrown ender pearls, dropped items, arrows - anything
        // that isn't a player but could still teleport through a portal.
        // Unrecognized portal block (target == null): fail closed only if
        // *something* is disabled, so we don't silently let an edge case
        // (e.g. an End Gateway variant) slip through untouched.
        boolean shouldCancel = target != null
                ? isDisabled(target)
                : (configManager.isNetherDisabled() || configManager.isEndDisabled());

        if (shouldCancel) {
            event.setCancelled(true);
        }
    }

    /** Removes a player's stale cooldown entry so the map doesn't hold a UUID for someone no longer online. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        warningCooldowns.remove(event.getPlayer().getUniqueId());
    }

    private boolean isDisabled(Dimension dimension) {
        if (dimension == null) {
            return false;
        }
        return dimension == Dimension.NETHER ? configManager.isNetherDisabled() : configManager.isEndDisabled();
    }

    private static Dimension fromCause(PlayerTeleportEvent.TeleportCause cause) {
        return switch (cause) {
            case NETHER_PORTAL -> Dimension.NETHER;
            case END_PORTAL, END_GATEWAY -> Dimension.END;
            default -> null;
        };
    }

    private static Dimension fromBlock(Material type) {
        if (type == Material.NETHER_PORTAL) {
            return Dimension.NETHER;
        }
        if (type == Material.END_PORTAL || type == Material.END_GATEWAY) {
            return Dimension.END;
        }
        return null;
    }

    /** Shoves the player back the way they came so they leave the portal block instead of re-triggering the event every tick. */
    private void pushOutOfPortal(Player player) {
        Vector pushback = player.getLocation().getDirection()
                .multiply(-1)
                .setY(0.2)
                .normalize()
                .multiply(Constants.Dimension.PORTAL_PUSHBACK_STRENGTH);
        player.setVelocity(pushback);
    }

    private void warnDimensionDisabled(Player player) {
        long now = System.currentTimeMillis();
        Long lastWarned = warningCooldowns.get(player.getUniqueId());
        if (lastWarned != null && now - lastWarned < Constants.Warnings.COOLDOWN_MS) {
            return;
        }
        warningCooldowns.put(player.getUniqueId(), now);
        player.sendMessage(Lang.DIMENSION_TRAVEL_DISABLED);
    }

    private enum Dimension {
        NETHER,
        END
    }
}