package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles element item drops and element reroll on player death.
 * 
 * When a player dies:
 * 1. Upgrade items are dropped matching their upgrade level
 * 2. Passive upsides are reapplied after a short delay
 */
public class ElementItemDeathListener implements Listener {
    
    private static final long REAPPLY_DELAY_TICKS = 1L;

    private final ElementSMPRefined plugin;
    private final ElementManager elements;

    public ElementItemDeathListener(ElementSMPRefined plugin, ElementManager elements) {
        this.plugin = plugin;
        this.elements = elements;
    }

    /**
     * Handle element item drops on player death.
     * Prioritized HIGH to run before other death handlers.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();
        PlayerData playerData = elements.data(deadPlayer.getUniqueId());

        // Null safety check
        if (playerData == null) {
            return;
        }

        if (playerData.getCurrentElement() != null) {
            handleUpgradeDrops(event, playerData, playerData.getCurrentElement());
        }
    }

    /**
     * Drop upgrade items matching the player's upgrade level.
     */
    private void handleUpgradeDrops(PlayerDeathEvent event, PlayerData playerData, ElementType currentElement) {
        int upgradeLevel = playerData.getUpgradeLevel(currentElement);

        if (upgradeLevel <= 0) {
            return;
        }

        // Drop Upgrader I
        if (upgradeLevel >= 1) {
            ItemStack upgrader1 = plugin.getItemManager().createUpgrader1();
            if (upgrader1 != null) {
                event.getDrops().add(upgrader1);
            }
        }

        // Drop Upgrader II
        if (upgradeLevel >= 2) {
            ItemStack upgrader2 = plugin.getItemManager().createUpgrader2();
            if (upgrader2 != null) {
                event.getDrops().add(upgrader2);
            }
        }

        // Reset upgrade level and save
        playerData.setUpgradeLevel(currentElement, 0);
        plugin.getDataStore().save(playerData);

        scheduleUpsideReapply(event.getEntity());
    }

    /**
     * Reapply passive upsides after a short delay.
     */
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
