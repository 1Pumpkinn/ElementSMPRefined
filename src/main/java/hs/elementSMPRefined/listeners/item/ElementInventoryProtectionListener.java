package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.bukkit.ItemUtil;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Prevents Life and Death cores from being stored in Ender Chests
 */
public class ElementInventoryProtectionListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elements;

    public ElementInventoryProtectionListener(ElementSMPRefined plugin, ElementManager elements) {
        this.plugin = plugin;
        this.elements = elements;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        Inventory topInventory = event.getView().getTopInventory();
        if (!isEnderChest(topInventory)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (isProtectedItem(cursor) || isProtectedItem(current)) {
            cancelWithMessage(event, player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        Inventory topInventory = event.getView().getTopInventory();
        if (!isEnderChest(topInventory)) return;

        ItemStack item = event.getOldCursor();
        if (isProtectedItem(item)) {
            cancelWithMessage(event, player);
        }
    }

    private boolean isEnderChest(Inventory inventory) {
        return inventory != null && inventory.getType() == InventoryType.ENDER_CHEST;
    }

    private boolean isProtectedItem(ItemStack stack) {
        if (stack == null || !ItemUtil.isElementItem(plugin, stack)) {
            return false;
        }
        
        Optional<ElementType> typeOpt = ItemUtil.getElementTypeOptional(plugin, stack);
        if (typeOpt.isEmpty()) return false;
        
        return isLifeOrDeath(typeOpt.get());
    }

    private boolean isLifeOrDeath(ElementType type) {
        return type == ElementType.LIFE || type == ElementType.DEATH;
    }

    private void cancelWithMessage(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You cannot store Life or Death cores in an Ender Chest!");
        SoundUtils.playTo(player, SoundUtils.UI.ERROR);
    }

    private void cancelWithMessage(InventoryDragEvent event, Player player) {
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You cannot store Life or Death cores in an Ender Chest!");
        SoundUtils.playTo(player, SoundUtils.UI.ERROR);
    }
}
