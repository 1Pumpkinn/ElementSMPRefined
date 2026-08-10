package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.bukkit.ItemUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ElementInventoryProtectionListener implements Listener {
    private final ElementSMPRefined plugin;
    private final hs.elementSMPRefined.managers.ElementManager elements;

    public ElementInventoryProtectionListener(ElementSMPRefined plugin, ElementManager elements) {
        this.plugin = plugin;
        this.elements = elements;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        Inventory top = event.getView().getTopInventory();

        if (top == null || top.getType() != InventoryType.ENDER_CHEST) return;

        if ((cursor != null && isLifeOrDeathCore(cursor)) || (current != null && isLifeOrDeathCore(current))) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot store Life or Death cores in an Ender Chest!");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getOldCursor();
        Inventory top = event.getView().getTopInventory();

        if (top == null || top.getType() != InventoryType.ENDER_CHEST) return;

        if (item != null && isLifeOrDeathCore(item)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot store Life or Death cores in an Ender Chest!");
        }
    }

    private boolean isLifeOrDeathCore(ItemStack stack) {
        if (!ItemUtil.isElementItem(plugin, stack)) return false;
        ElementType type = ItemUtil.getElementType(plugin, stack);
        return type == ElementType.LIFE || type == ElementType.DEATH;
    }
}

