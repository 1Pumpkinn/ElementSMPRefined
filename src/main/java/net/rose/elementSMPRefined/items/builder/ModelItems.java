package net.rose.elementSMPRefined.items.builder;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache of the "model carrier" items used by every ItemDisplay visual in the plugin.
 * <p>
 * A display only needs a stick wearing a resource-pack {@code item_model}. Building that through
 * {@link ItemBuilder} runs {@code editMeta} (meta copy + apply) every single time, and the visuals
 * did it once per spawned entity - profiling showed it as a large slice of the Ice Spikes cost.
 * The stack is now built once per model key and shared.
 * <p>
 * The returned stack is SHARED: pass it straight to {@code ItemDisplay#setItemStack} (which copies it)
 * and never mutate it. Call {@code .clone()} first if you need to change it.
 */
public final class ModelItems {
    private static final Map<NamespacedKey, ItemStack> CACHE = new ConcurrentHashMap<>();

    private ModelItems() {}

    /** A stick whose model is {@code model}. Built once, then reused. */
    public static ItemStack of(NamespacedKey model) {
        return CACHE.computeIfAbsent(model,
                key -> ItemBuilder.of(Material.STICK).itemModel(key).build());
    }
}
