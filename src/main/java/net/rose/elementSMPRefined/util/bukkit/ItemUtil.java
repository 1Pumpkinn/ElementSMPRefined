package net.rose.elementSMPRefined.util.bukkit;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.ItemKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

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
     * Get the element type from an item stack. Reads both plain names ("FIRE")
     * and the old "elements:fire" tag format.
     */
    public static ElementType getElementType(ElementSMPRefined plugin, ItemStack stack) {
        return getTag(stack, ItemKeys.elementType(plugin), PersistentDataType.STRING)
                .flatMap(ElementType::parse)
                .orElse(null);
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

}

