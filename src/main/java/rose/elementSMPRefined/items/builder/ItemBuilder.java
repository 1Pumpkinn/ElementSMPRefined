package rose.elementSMPRefined.items.builder;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.logging.Level;

/**
 * The single fluent builder for creating and modifying ItemStacks in this plugin.
 * <p>
 * There used to be two of these ({@code items.builder.ItemBuilder} and an inner
 * class of {@code util.bukkit.ItemUtil}) plus ~11 call sites that hand-rolled the
 * same {@code new ItemStack -> getItemMeta -> setLore/setDisplayName -> PDC.set ->
 * setItemMeta} sequence independently. This class replaces all of that - if you
 * need a feature this builder doesn't have yet, add it here rather than
 * hand-rolling ItemMeta code at the call site again.
 */
public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    private ItemBuilder(ItemStack item) {
        this.item = item;
        this.meta = item.getItemMeta();
        if (this.meta == null) {
            // Only really happens for AIR. Every call below no-ops safely when
            // this is null, but log it since it usually means a Material mistake.
            Bukkit.getLogger().log(Level.WARNING,
                    "ItemBuilder: getItemMeta() returned null for " + item.getType()
                            + " - name/lore/enchant/data calls will be ignored for this item.");
        }
    }

    /** Start building a fresh item of the given material (amount 1). */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    /** Start building a fresh item of the given material and amount. */
    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(new ItemStack(material, amount));
    }

    /**
     * Start building from a clone of an existing stack, preserving its current
     * meta. Use this when updating an item already sitting in a GUI/inventory
     * (e.g. changing just the lore) instead of recreating it from scratch.
     */
    public static ItemBuilder from(ItemStack stack) {
        return new ItemBuilder(stack.clone());
    }

    /** Sets the display name, translating {@code &} color codes. */
    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        return this;
    }

    /**
     * Sets the display name as-is, with no {@code &} color-code translation.
     * Use this when the string is already resolved (e.g. built from a
     * {@link ChatColor} constant rather than a literal {@code "&x"} string).
     */
    public ItemBuilder rawName(String name) {
        if (meta != null) meta.setDisplayName(name);
        return this;
    }

    /** Sets the lore, translating {@code &} color codes on each line. */
    public ItemBuilder lore(String... lines) {
        return lore(List.of(lines));
    }

    /** Sets the lore, translating {@code &} color codes on each line. */
    public ItemBuilder lore(List<String> lines) {
        List<String> translated = new ArrayList<>(lines.size());
        for (String line : lines) {
            translated.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        if (meta != null) meta.setLore(translated);
        return this;
    }

    /** Sets the lore as-is, with no {@code &} color-code translation. */
    public ItemBuilder rawLore(List<String> lines) {
        if (meta != null) meta.setLore(lines);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder unbreakable() {
        return unbreakable(true);
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        if (meta != null) meta.setUnbreakable(unbreakable);
        return this;
    }

    public ItemBuilder hideAttributes() {
        if (meta != null) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return this;
    }

    public ItemBuilder hideEnchants() {
        if (meta != null) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder customModelData(int data) {
        if (meta != null) meta.setCustomModelData(data);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null) meta.addEnchant(enchantment, level, true);
        return this;
    }

    /** Sets a String value in the item's PersistentDataContainer. */
    public ItemBuilder data(NamespacedKey key, String value) {
        if (meta != null) meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    /** Sets an Integer value in the item's PersistentDataContainer. */
    public ItemBuilder data(NamespacedKey key, int value) {
        if (meta != null) meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        return this;
    }

    /** Sets a Byte value in the item's PersistentDataContainer (commonly used as a boolean flag). */
    public ItemBuilder data(NamespacedKey key, byte value) {
        if (meta != null) meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, value);
        return this;
    }

    /** Escape hatch for meta operations this builder doesn't wrap yet. */
    public ItemBuilder meta(Consumer<ItemMeta> modifier) {
        if (meta != null) modifier.accept(meta);
        return this;
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }
}
