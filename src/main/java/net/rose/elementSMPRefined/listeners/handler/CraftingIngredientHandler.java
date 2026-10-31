package net.rose.elementSMPRefined.listeners.handler;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Stops the plugin's custom items from being used as crafting ingredients.
 * <p>
 * Upgrader I/II are plain amethyst shards and the Forged Upgrader is a plain
 * ender eye as far as vanilla recipe matching goes (the recipes only check
 * {@code Material}), so without this an Upgrader I would satisfy the "A" slot
 * of the Upgrader I recipe, a Forged Upgrader would satisfy the "E" slot, etc.
 * <p>
 * Covers the Upgraders (I, II), Forged Upgrader and Element Reroller.
 */
public class CraftingIngredientHandler implements Listener {
    private final ElementSMPRefined plugin;

    public CraftingIngredientHandler(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    /** Clears the result preview so the recipe never "matches" with a custom item in the grid. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsCustomIngredient(event.getInventory())) {
            event.getInventory().setResult(null);
        }
    }

    /** Safety net for shift-click / recipe-book edge cases where the preview was already set. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (containsCustomIngredient(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    private boolean containsCustomIngredient(CraftingInventory inventory) {
        for (ItemStack item : inventory.getMatrix()) {
            if (isProtectedItem(item)) return true;
        }
        return false;
    }

    private boolean isProtectedItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;

        return ItemUtil.hasTag(item, ItemKeys.upgraderLevel(plugin), PersistentDataType.INTEGER)
                || ItemUtil.hasTag(item, ItemKeys.forgedUpgrader(plugin), PersistentDataType.BYTE)
                || ItemUtil.hasTag(item, ItemKeys.reroller(plugin), PersistentDataType.BYTE);
    }
}