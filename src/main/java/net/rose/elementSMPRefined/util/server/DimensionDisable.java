package net.rose.elementSMPRefined.util.server;

import net.rose.elementSMPRefined.commands.server.DimensionCommand;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
 * (runtime-toggleable with /dimension, see {@link DimensionCommand}).
 * <p>
 * How the block behaves is configurable too (config.yml {@code dimensions.*},
 * or {@code /dimension set}): whether players are warned and how often,
 * whether they're pushed back, whether non-player entities are blocked, and
 * whether {@code element.admin} players bypass it.
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

        Player player = event.getPlayer();
        if (configManager.isDimensionAdminBypass() && player.hasPermission("element.admin")) {
            return;
        }

        event.setCancelled(true);

        if (configManager.isDimensionPushbackEnabled()) {
            pushOutOfPortal(player);
        }
        if (configManager.isDimensionWarnPlayers()) {
            warnDimensionDisabled(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!configManager.isDimensionBlockEntities()) {
            return;
        }

        // Work out which dimension the portal leads to from the worlds on either side.
        // This keeps boats, minecarts, mounts and other entities moving normally through
        // an enabled dimension, even if the other one is disabled.
        Dimension target = fromWorlds(event);
        if (target == null) {
            target = fromBlock(event.getFrom().getBlock().getType());
        }

        // Only an unidentifiable portal fails closed, and only when something is disabled.
        boolean shouldCancel =
                target != null
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
        return dimension == Dimension.NETHER
                ? configManager.isNetherDisabled()
                : configManager.isEndDisabled();
    }

    private static Dimension fromCause(PlayerTeleportEvent.TeleportCause cause) {
        return switch (cause) {
            case NETHER_PORTAL -> Dimension.NETHER;
            case END_PORTAL, END_GATEWAY -> Dimension.END;
            default -> null;
        };
    }

    private static Dimension fromWorlds(EntityPortalEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return null;
        }
        World.Environment from = event.getFrom().getWorld().getEnvironment();
        World.Environment dest = to.getWorld().getEnvironment();
        if (from == World.Environment.NETHER || dest == World.Environment.NETHER) {
            return Dimension.NETHER;
        }
        if (from == World.Environment.THE_END || dest == World.Environment.THE_END) {
            return Dimension.END;
        }
        return null;
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

    private void pushOutOfPortal(Player player) {
        Vector pushback =
                player.getLocation()
                        .getDirection()
                        .multiply(-1)
                        .setY(0.2)
                        .normalize()
                        .multiply(configManager.getDimensionPushbackStrength());
        player.setVelocity(pushback);
    }

    private void warnDimensionDisabled(Player player) {
        long now = System.currentTimeMillis();
        long cooldownMs = configManager.getDimensionWarningCooldownSeconds() * 1000L;
        Long lastWarned = warningCooldowns.get(player.getUniqueId());
        if (lastWarned != null && now - lastWarned < cooldownMs) {
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
