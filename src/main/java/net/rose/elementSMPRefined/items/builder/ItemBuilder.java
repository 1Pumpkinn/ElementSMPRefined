package net.rose.elementSMPRefined.items.builder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The single fluent builder for creating and modifying ItemStacks in this plugin.
 * If you need a feature that isn't here yet, add it here rather than hand-rolling
 * ItemMeta code at the call site.
 * <p>
 * Text handling:
 * <ul>
 *   <li>{@code String} overloads accept legacy {@code &} color codes.</li>
 *   <li>{@code Component} overloads are used as-is.</li>
 *   <li>Names and lore have vanilla's default italics switched off (unless the text
 *       explicitly sets italics), so they render the way you wrote them.</li>
 * </ul>
 * Every method edits the stack immediately via {@link ItemStack#editMeta}, so there
 * is no separate "commit" step to forget and nothing to null-check.
 */
public final class ItemBuilder {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemStack item;

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }


    /** Starts a fresh item of the given material (amount 1). */
    public static ItemBuilder of(Material material) {
        return of(material, 1);
    }

    /** Starts a fresh item of the given material and amount. */
    public static ItemBuilder of(Material material, int amount) {
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("ItemBuilder cannot build an item from " + material);
        }
        return new ItemBuilder(new ItemStack(material, amount));
    }

    /**
     * Starts from a clone of an existing stack, preserving its meta. The original
     * stack is never modified.
     */
    public static ItemBuilder from(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            throw new IllegalArgumentException("ItemBuilder cannot build from an empty stack");
        }
        return new ItemBuilder(stack.clone());
    }



    /** Sets the display name, translating {@code &} color codes. */
    public ItemBuilder name(String name) {
        return name(LEGACY.deserialize(name));
    }

    /** Sets the display name. */
    public ItemBuilder name(Component name) {
        Component styled = noItalic(name);
        return edit(meta -> meta.displayName(styled));
    }

    /** Replaces the lore, translating {@code &} color codes on each line. */
    public ItemBuilder lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) out.add(LEGACY.deserialize(line));
        return lore(out);
    }

    /** Replaces the lore. */
    public ItemBuilder lore(Component... lines) {
        return lore(List.of(lines));
    }

    /** Replaces the lore. */
    public ItemBuilder lore(List<Component> lines) {
        List<Component> styled = new ArrayList<>(lines.size());
        for (Component line : lines) styled.add(noItalic(line));
        return edit(meta -> meta.lore(styled));
    }

    /** Appends lines to the existing lore, translating {@code &} color codes. */
    public ItemBuilder addLore(String... lines) {
        List<Component> combined = new ArrayList<>();
        List<Component> existing = item.lore();
        if (existing != null) combined.addAll(existing);
        for (String line : lines) combined.add(LEGACY.deserialize(line));
        return lore(combined);
    }


    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder unbreakable() {
        return edit(meta -> meta.setUnbreakable(true));
    }

    /** Points the item at a resource-pack model, e.g. {@code elementsmprefined:chain}. */
    public ItemBuilder itemModel(NamespacedKey model) {
        return edit(meta -> meta.setItemModel(model));
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        return edit(meta -> meta.addEnchant(enchantment, level, true));
    }

    /** Shows the enchantment glint without adding a real enchantment. */
    public ItemBuilder glow() {
        return edit(meta -> meta.setEnchantmentGlintOverride(true));
    }

    public ItemBuilder flags(ItemFlag... flags) {
        return edit(meta -> meta.addItemFlags(flags));
    }


    /** Sets a value in the item's PersistentDataContainer. */
    public <T, Z> ItemBuilder data(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        return edit(meta -> meta.getPersistentDataContainer().set(key, type, value));
    }

    public ItemBuilder data(NamespacedKey key, String value) {
        return data(key, PersistentDataType.STRING, value);
    }

    public ItemBuilder data(NamespacedKey key, int value) {
        return data(key, PersistentDataType.INTEGER, value);
    }

    /** Byte overload, commonly used as a boolean flag: {@code data(key, (byte) 1)}. */
    public ItemBuilder data(NamespacedKey key, byte value) {
        return data(key, PersistentDataType.BYTE, value);
    }

    /** Marks the item with a boolean flag (stored as byte 1). */
    public ItemBuilder flag(NamespacedKey key) {
        return data(key, (byte) 1);
    }


    /** For meta operations this builder doesn't wrap yet. */
    public ItemBuilder edit(Consumer<ItemMeta> modifier) {
        item.editMeta(modifier);
        return this;
    }

    /** Returns the built stack. The builder keeps ownership - call {@link #build()} once. */
    public ItemStack build() {
        return item;
    }

    /** Vanilla italicises custom names/lore by default; turn that off unless the text set it. */
    private static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
