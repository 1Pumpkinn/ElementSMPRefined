package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.managers.ItemManager;
import hs.elementSMPRefined.util.bukkit.ItemUtil;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all element item interactions: drop and use
 */
public class ElementItemInteractionListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ItemManager itemManager;

    public ElementItemInteractionListener(ElementSMPRefined plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && isElementItem(item)) {
            SoundUtils.playTo(event.getPlayer(), SoundUtils.UI.CLICK);
            itemManager.handleUse(event);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (isElementItem(stack)) {
            Player player = event.getPlayer();
            // Currently does nothing but could be extended for drop protection
            if (player.isDead() || player.getHealth() <= 0) {
                // Logic for dead players could be added here
            }
        }
    }

    private boolean isElementItem(ItemStack stack) {
        return ItemUtil.isElementItem(plugin, stack);
    }
}