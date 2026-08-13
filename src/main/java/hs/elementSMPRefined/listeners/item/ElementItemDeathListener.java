package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles element item behavior on player death
 */
public class ElementItemDeathListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elements;

    public ElementItemDeathListener(ElementSMPRefined plugin, ElementManager elements) {
        this.plugin = plugin;
        this.elements = elements;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        PlayerData playerData = elements.data(event.getEntity().getUniqueId());
        ElementType currentElement = playerData.getCurrentElement();

        if (currentElement != null) {
            handleUpgradeDrops(event, playerData, currentElement);
            handleCoreDrop(event, playerData, currentElement);
        }
    }

    private void handleUpgradeDrops(PlayerDeathEvent event, PlayerData playerData, ElementType currentElement) {
        int currentLevel = playerData.getUpgradeLevel(currentElement);

        if (currentLevel > 0) {
            for (int i = 0; i < currentLevel; i++) {
                ItemStack upgrader = (i == 0) 
                        ? plugin.getItemManager().createUpgrader1()
                        : plugin.getItemManager().createUpgrader2();
                event.getDrops().add(upgrader);
            }

            playerData.setUpgradeLevel(currentElement, 0);
            plugin.getDataStore().save(playerData);

            scheduleUpsideReapply(event);
        }
    }

    private void handleCoreDrop(PlayerDeathEvent event, PlayerData playerData, ElementType currentElement) {
        if (!shouldDropCore(currentElement)) {
            return;
        }

        ItemStack coreItem = hs.elementSMPRefined.items.ElementCoreItem.createCore(plugin, currentElement);
        if (coreItem != null) {
            event.getDrops().add(coreItem);
        }

        playerData.removeElementItem(currentElement);
        plugin.getDataStore().save(playerData);

        scheduleElementReroll(event);
    }

    private void scheduleUpsideReapply(PlayerDeathEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (event.getEntity().isOnline()) {
                    elements.applyUpsides(event.getEntity());
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    private void scheduleElementReroll(PlayerDeathEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (event.getEntity().isOnline()) {
                    elements.assignRandomDifferentElement(event.getEntity());
                    event.getEntity().sendMessage(ChatColor.YELLOW + 
                            "Your core dropped and you rolled a new element!");
                }
            }
        }.runTaskLater(plugin, 40L);
    }

    private boolean shouldDropCore(ElementType type) {
        return type == ElementType.LIFE || type == ElementType.DEATH;
    }
}
