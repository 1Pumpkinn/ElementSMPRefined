package net.rose.elementSMPRefined.managers;

import net.rose.elementSMPRefined.items.recipes.Upgrader1Item;
import net.rose.elementSMPRefined.items.recipes.Upgrader2Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Factory for the plugin's craftable items. Use/damage handling lives in the
 * dedicated handlers (RerollerHandler, UpgraderHandler, ...), not here.
 */
public class ItemManager {
    private final JavaPlugin plugin;

    public ItemManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createUpgrader1() {
        return Upgrader1Item.make(plugin);
    }

    public ItemStack createUpgrader2() {
        return Upgrader2Item.make(plugin);
    }
}
