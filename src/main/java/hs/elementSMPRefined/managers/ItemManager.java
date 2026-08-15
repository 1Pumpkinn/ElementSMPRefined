package hs.elementSMPRefined.managers;

import hs.elementSMPRefined.items.api.ElementItem;
import hs.elementSMPRefined.registry.ItemRegistry;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

public class ItemManager {
    private final JavaPlugin plugin;
    private final ManaManager mana;
    private final ConfigManager configManager;
    private final ItemRegistry itemRegistry;

    public ItemManager(JavaPlugin plugin, ManaManager mana, ConfigManager configManager) {
        this.plugin = plugin;
        this.mana = mana;
        this.configManager = configManager;
        this.itemRegistry = new ItemRegistry(plugin);
        registerDefaultItems();
        itemRegistry.freeze();
    }

    /**
     * Register default items using the new registry system
     */
    private void registerDefaultItems() {
        // Element core items are registered dynamically based on element type
        // These are handled by ElementManager.giveElementItem()
    }

    public void register(ElementItem item) {
        // This method is kept for backward compatibility
        // Items should ideally be registered through the registry
        String itemId = item.getClass().getSimpleName().toLowerCase();
        itemRegistry.register(itemId, item, ItemRegistry.ItemData.builder()
                .displayName(itemId)
                .description("Custom item")
                .build());
        item.registerRecipe(plugin);
    }

    public void handleUse(PlayerInteractEvent e) {
        for (ElementItem item : itemRegistry.getAllItems()) {
            if (item.handleUse(e, plugin, mana, configManager)) {
                // If an item handled the event, stop processing
                return;
            }
        }
    }

    public void handleDamage(EntityDamageByEntityEvent e) {
        for (ElementItem item : itemRegistry.getAllItems()) {
            item.handleDamage(e, plugin);
        }
    }

    public void handleLaunch(ProjectileLaunchEvent e) {
        for (ElementItem item : itemRegistry.getAllItems()) {
            item.handleLaunch(e, plugin, mana, configManager);
        }
    }

    /**
     * Get an item by its ID
     */
    public ElementItem getItem(String itemId) {
        return itemRegistry.getItem(itemId);
    }

    /**
     * Get all registered items
     */
    public Collection<ElementItem> getAllItems() {
        return itemRegistry.getAllItems();
    }

    /**
     * Get the item registry for advanced usage
     */
    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    /**
     * Creates an Upgrader1 item
     * @return The created ItemStack
     */
    public ItemStack createUpgrader1() {
        return hs.elementSMPRefined.items.Upgrader1Item.make(plugin);
    }

    /**
     * Creates an Upgrader2 item
     * @return The created ItemStack
     */
    public ItemStack createUpgrader2() {
        return hs.elementSMPRefined.items.Upgrader2Item.make(plugin);
    }
}