package hs.elementSMPRefined.util.bukkit;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.element.ElementId;
import hs.elementSMPRefined.items.ItemKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
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
     * Safely get an item's PersistentDataContainer, if it has meta at all.
     * Replaces the repeated {@code item.hasItemMeta() ? item.getItemMeta()
     * .getPersistentDataContainer() : ...} null-check dance that used to be
     * copy-pasted across GUIListener, UpgraderHandler, RerollerHandler,
     * AdvancedRerollerHandler, and ElementItemCraftingListener.
     */
    public static Optional<PersistentDataContainer> pdc(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        return Optional.of(stack.getItemMeta().getPersistentDataContainer());
    }

    /**
     * Check whether an item has a given PersistentDataContainer key set at all,
     * regardless of value - useful for boolean-flag-style tags.
     */
    public static <T, Z> boolean hasTag(ItemStack stack, NamespacedKey key, PersistentDataType<T, Z> type) {
        return pdc(stack).map(c -> c.has(key, type)).orElse(false);
    }

    /**
     * Read a PersistentDataContainer value from an item, if present.
     */
    public static <T, Z> Optional<Z> getTag(ItemStack stack, NamespacedKey key, PersistentDataType<T, Z> type) {
        return pdc(stack).map(c -> c.get(key, type));
    }

    /**
     * Check if an item stack is an element item
     */
    public static boolean isElementItem(ElementSMPRefined plugin, ItemStack stack) {
        Byte flag = getTag(stack, ItemKeys.elementItem(plugin), PersistentDataType.BYTE).orElse(null);
        return flag != null && flag == (byte)1;
    }

    /**
     * Get the element type from an item stack (legacy method for backward compatibility)
     */
    public static ElementType getElementType(ElementSMPRefined plugin, ItemStack stack) {
        try {
            Optional<ElementId> id = getElementIdOptional(plugin, stack);
            if (id.isEmpty() || !id.get().namespace().equals("elements")) return null;
            return ElementType.valueOf(id.get().key().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get the element type from an item stack (Optional version for new code)
     */
    public static Optional<ElementType> getElementTypeOptional(ElementSMPRefined plugin, ItemStack stack) {
        try {
            return Optional.ofNullable(getElementType(plugin, stack));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Read both canonical namespaced IDs and legacy enum names from core items. */
    public static Optional<ElementId> getElementIdOptional(ElementSMPRefined plugin, ItemStack stack) {
        String value = getTag(stack, ItemKeys.elementType(plugin), PersistentDataType.STRING).orElse(null);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(value.contains(":")
                    ? ElementId.parse(value)
                    : ElementId.builtin(ElementType.valueOf(value)));
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
                    new NamespacedKey(plugin, key),
                    PersistentDataType.STRING,
                    value
            );
        });
    }

    /**
     * Get a custom persistent data value from an item
     */
    public static Optional<String> getCustomData(ElementSMPRefined plugin, ItemStack stack, String key) {
        return getTag(stack, new NamespacedKey(plugin, key), PersistentDataType.STRING);
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

}

