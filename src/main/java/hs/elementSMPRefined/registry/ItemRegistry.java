package hs.elementSMPRefined.registry;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.items.api.ElementItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Central registry for all custom items. Provides automatic registration and lookup.
 * Replaces manual item registration in ItemManager.
 */
public class ItemRegistry {
    private final ElementSMPRefined plugin;
    private final Map<String, RegisteredItem> items = new HashMap<>();
    private final Map<String, ElementItem> elementItems = new HashMap<>();
    private boolean frozen = false;

    public ItemRegistry(JavaPlugin plugin) {
        this.plugin = (ElementSMPRefined) plugin;
    }

    /**
     * Register a custom item
     * @param itemId Unique identifier for the item
     * @param item The item instance
     * @param data The item's metadata
     */
    public void register(String itemId, ElementItem item, ItemData data) {
        if (frozen) {
            throw new IllegalStateException("Registry is frozen and cannot accept new registrations");
        }

        if (items.containsKey(itemId)) {
            plugin.getLogger().warning("Item " + itemId + " is already registered. Skipping duplicate.");
            return;
        }

        items.put(itemId, new RegisteredItem(item, data));
        elementItems.put(itemId, item);
    }

    /** Register an item supplied after the built-in registry has frozen. */
    public void registerAddon(String itemId, ElementItem item, ItemData data) {
        if (itemId == null || itemId.isBlank() || item == null || data == null) {
            throw new IllegalArgumentException("Item ID, item, and item data are required");
        }
        if (items.putIfAbsent(itemId, new RegisteredItem(item, data)) != null) {
            throw new IllegalArgumentException("Item " + itemId + " is already registered");
        }
        elementItems.put(itemId, item);
    }

    /**
     * Get an item by its ID
     */
    public ElementItem getItem(String itemId) {
        RegisteredItem registered = items.get(itemId);
        return registered != null ? registered.item() : null;
    }

    /**
     * Get item data by its ID
     */
    public ItemData getData(String itemId) {
        RegisteredItem registered = items.get(itemId);
        return registered != null ? registered.data() : null;
    }

    /**
     * Get all registered items
     */
    public Collection<ElementItem> getAllItems() {
        return Collections.unmodifiableCollection(elementItems.values());
    }

    /**
     * Get all registered item IDs
     */
    public Set<String> getAllIds() {
        return Collections.unmodifiableSet(items.keySet());
    }

    /**
     * Check if an item is registered
     */
    public boolean isRegistered(String itemId) {
        return items.containsKey(itemId);
    }

    /**
     * Freeze the registry to prevent further registrations
     */
    public void freeze() {
        this.frozen = true;
    }

    /**
     * Get the number of registered items
     */
    public int size() {
        return items.size();
    }

    /**
     * Data class for item metadata
     */
    public record ItemData(
            String displayName,
            String description,
            Material material,
            String[] lore,
            boolean isConsumable,
            int maxStackSize,
            boolean requiresPermission
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String displayName;
            private String description;
            private Material material;
            private String[] lore = new String[0];
            private boolean isConsumable = false;
            private int maxStackSize = 64;
            private boolean requiresPermission = false;

            public Builder displayName(String displayName) {
                this.displayName = displayName;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder material(Material material) {
                this.material = material;
                return this;
            }

            public Builder lore(String... lore) {
                this.lore = lore;
                return this;
            }

            public Builder isConsumable(boolean isConsumable) {
                this.isConsumable = isConsumable;
                return this;
            }

            public Builder maxStackSize(int maxStackSize) {
                this.maxStackSize = maxStackSize;
                return this;
            }

            public Builder requiresPermission(boolean requiresPermission) {
                this.requiresPermission = requiresPermission;
                return this;
            }

            public ItemData build() {
                if (displayName == null) {
                    throw new IllegalStateException("Display name is required");
                }
                if (material == null) {
                    throw new IllegalStateException("Material is required");
                }
                return new ItemData(displayName, description, material, lore, isConsumable, maxStackSize, requiresPermission);
            }
        }
    }

    /**
     * Internal record to hold item and its data together
     */
    private record RegisteredItem(ElementItem item, ItemData data) {}
}