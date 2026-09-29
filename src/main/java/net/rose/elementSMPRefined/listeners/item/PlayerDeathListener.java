package net.rose.elementSMPRefined.listeners.item;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles upgrader drops when a player dies.
 */
public class PlayerDeathListener implements Listener {

    private static final long REAPPLY_DELAY_TICKS = 1L;

    private final ElementSMPRefined plugin;
    private final ElementManager elements;

    public PlayerDeathListener(ElementSMPRefined plugin, ElementManager elements) {
        this.plugin = plugin;
        this.elements = elements;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData data = elements.data(player.getUniqueId());
        if (data == null) {
            return;
        }

        ElementType element = data.getCurrentElement();
        if (element == null) {
            return;
        }

        // With keepInventory the player keeps their items, so they must keep their level too
        if (event.getKeepInventory()) {
            return;
        }

        int level = data.getUpgradeLevel(element);
        if (level <= 0) {
            return;
        }

        ItemStack drop = createUpgraderFor(level);
        if (drop == null) {
            // Item couldn't be created, so don't strip the level for nothing
            plugin.getLogger().warning("Could not create upgrader for level " + level
                    + " (player " + player.getName() + ")");
            return;
        }

        event.getDrops().add(drop);

        // Lose exactly one level
        data.setUpgradeLevel(element, level - 1);
        plugin.getDataStore().save(data);

        scheduleUpsideReapply(player);
    }

    /**
     * Returns the upgrader item matching the given (current) upgrade level.
     */
    private ItemStack createUpgraderFor(int level) {
        return switch (level) {
            case 1 -> plugin.getItemManager().createUpgrader1();
            case 2 -> plugin.getItemManager().createUpgrader2();
            default -> null;
        };
    }

    private void scheduleUpsideReapply(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    elements.applyUpsides(player);
                }
            }
        }.runTaskLater(plugin, REAPPLY_DELAY_TICKS);
    }
}