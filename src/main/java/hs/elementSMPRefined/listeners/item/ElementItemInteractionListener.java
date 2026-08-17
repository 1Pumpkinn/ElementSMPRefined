package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.managers.ItemManager;
import hs.elementSMPRefined.util.bukkit.ItemUtil;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Handles all element item interactions: pickup, drop, and use
 */
public class ElementItemInteractionListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elements;
    private final ItemManager itemManager;

    public ElementItemInteractionListener(ElementSMPRefined plugin, ElementManager elements, ItemManager itemManager) {
        this.plugin = plugin;
        this.elements = elements;
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
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack stack = event.getItem().getItemStack();
        if (!isElementItem(stack)) return;

        Optional<ElementType> typeOpt = ItemUtil.getElementTypeOptional(plugin, stack);
        if (typeOpt.isEmpty()) return;

        ElementType type = typeOpt.get();

        PlayerData playerData = elements.data(player.getUniqueId());
        if (playerData.getCurrentElement() != type) {
            elements.setElement(player, type);
            SoundUtils.playTo(player, SoundUtils.UI.SELECT);
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