package hs.elementSMPRefined.util.bukkit;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.items.ItemKeys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Enhanced utility class for ItemStack operations and element item management.
 * Provides safe, null-checked operations with Optional returns and builder patterns.
 */
public final class ItemUtil {
    private ItemUtil() {}

    /**
     * Check if an item stack is an element item
     */
    public static boolean isElementItem(ElementSMPRefined plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        Byte flag = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.elementItem(plugin), PersistentDataType.BYTE);
        return flag != null && flag == (byte)1;
    }

    /**
     * Get the element type from an item stack (legacy method for backward compatibility)
     */
    public static ElementType getElementType(ElementSMPRefined plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String typeStr = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.elementType(plugin), PersistentDataType.STRING);
        if (typeStr == null) return null;
        try {
            return ElementType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get the element type from an item stack (Optional version for new code)
     */
    public static Optional<ElementType> getElementTypeOptional(ElementSMPRefined plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        String typeStr = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.elementType(plugin), PersistentDataType.STRING);
        if (typeStr == null) return Optional.empty();
        try {
            return Optional.of(ElementType.valueOf(typeStr));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Safely modify item meta with automatic restoration
     */
    public static ItemStack modifyMeta(ItemStack stack, Consumer<ItemMeta> modifier) {
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        modifier.accept(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Set a custom persistent data value on an item
     */
    public static ItemStack setCustomData(ElementSMPRefined plugin, ItemStack stack, String key, String value) {
        return modifyMeta(stack, meta -> {
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, key),
                    PersistentDataType.STRING,
                    value
            );
        });
    }

    /**
     * Get a custom persistent data value from an item
     */
    public static Optional<String> getCustomData(ElementSMPRefined plugin, ItemStack stack, String key) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        String value = stack.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, key), PersistentDataType.STRING);
        return Optional.ofNullable(value);
    }

    /**
     * Create a builder for item stack creation
     */
    public static ItemBuilder builder(Material material) {
        return new ItemBuilder(material);
    }

    /**
     * Create a builder from existing item stack
     */
    public static ItemBuilder builder(ItemStack stack) {
        return new ItemBuilder(stack);
    }

    /**
     * Check if two item stacks are similar (same type, data, ignoring amount)
     */
    public static boolean isSimilar(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a == b) return true;

        ItemStack aCopy = a.clone();
        ItemStack bCopy = b.clone();
        aCopy.setAmount(1);
        bCopy.setAmount(1);

        return aCopy.isSimilar(bCopy);
    }

    /**
     * Get the total amount of matching items in an array
     */
    public static int countMatching(ItemStack[] items, ItemStack match) {
        if (items == null || match == null) return 0;

        int count = 0;
        for (ItemStack item : items) {
            if (item != null && isSimilar(item, match)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Remove a specific amount of matching items from an array
     */
    public static int removeMatching(ItemStack[] items, ItemStack match, int amount) {
        if (items == null || match == null || amount <= 0) return 0;

        int remaining = amount;
        for (int i = 0; i < items.length && remaining > 0; i++) {
            ItemStack item = items[i];
            if (item != null && isSimilar(item, match)) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    items[i] = null;
                    remaining -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
            }
        }
        return amount - remaining;
    }

    /**
     * Builder class for creating ItemStacks with a fluent API
     */
    public static class ItemBuilder {
        private final ItemStack item;
        private final ItemMeta meta;

        public ItemBuilder(Material material) {
            this.item = new ItemStack(material);
            this.meta = item.getItemMeta();
        }

        public ItemBuilder(ItemStack stack) {
            this.item = stack.clone();
            this.meta = item.getItemMeta();
        }

        public ItemBuilder amount(int amount) {
            item.setAmount(amount);
            return this;
        }

        public ItemBuilder name(String name) {
            meta.setDisplayName(name);
            return this;
        }

        public ItemBuilder lore(String... lore) {
            meta.setLore(java.util.Arrays.asList(lore));
            return this;
        }

        public ItemBuilder unbreakable(boolean unbreakable) {
            meta.setUnbreakable(unbreakable);
            return this;
        }

        public ItemBuilder customModelData(int data) {
            meta.setCustomModelData(data);
            return this;
        }

        public ItemBuilder enchant(org.bukkit.enchantments.Enchantment enchantment, int level) {
            meta.addEnchant(enchantment, level, true);
            return this;
        }

        public ItemBuilder persistentData(ElementSMPRefined plugin, String key, String value) {
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, key),
                    PersistentDataType.STRING,
                    value
            );
            return this;
        }

        public ItemBuilder persistentData(ElementSMPRefined plugin, String key, int value) {
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, key),
                    PersistentDataType.INTEGER,
                    value
            );
            return this;
        }

        public ItemStack build() {
            item.setItemMeta(meta);
            return item;
        }
    }
}

