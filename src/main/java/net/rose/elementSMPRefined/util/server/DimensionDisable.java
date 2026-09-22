package net.rose.elementSMPRefined.util.server;

import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Keeps everyone on the overworld by cancelling any attempt to travel through
 * a Nether or End portal - this is an element-based SMP, not a vanilla
 * dimension-hopping one.
 * <p>
 * Two events are cancelled because Bukkit fires a different event depending
 * on what enters the portal:
 * <ul>
 *   <li>{@link PlayerPortalEvent} - a player walking into a portal directly.</li>
 *   <li>{@link EntityPortalEvent} - any other entity entering a portal. This
 *   is what covers vehicles a player might be riding through (boats, horses,
 *   minecarts) and other entities that can end up inside one (thrown ender
 *   pearls, dropped items, arrows).</li>
 * </ul>
 * Cancelling both means nobody leaves the overworld, whether they walk in on
 * foot, ride a vehicle in, or get flung in some other way.
 */
public class DimensionDisable implements Listener {
    private final ConfigManager configManager;

    public DimensionDisable(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!configManager.isDimensionTravelDisabled()) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        player.sendMessage(Lang.DIMENSION_TRAVEL_DISABLED);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!configManager.isDimensionTravelDisabled()) {
            return;
        }

        // Covers vehicles (boats, horses, minecarts) with or without a
        // passenger, thrown ender pearls, dropped items, arrows - anything
        // that isn't a player but could still teleport through a portal.
        event.setCancelled(true);
    }
}