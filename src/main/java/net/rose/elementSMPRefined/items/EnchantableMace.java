package net.rose.elementSMPRefined.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The one mace that is allowed to keep its enchantments. Every other mace is stripped of
 * enchantments and can't be enchanted (see {@code MaceEnchantGuard}).
 * <p>
 * It is a plain mace with no custom name - the only difference is a persistent data tag
 * ({@link ItemKeys#enchantableMace}, i.e. {@code elementsmp:enchantable_mace} as a byte 1)
 * that the server checks for. Anything else that strips enchantments (e.g. an enchant limits
 * plugin) can exempt a mace carrying that tag.
 */
public final class EnchantableMace {
    private EnchantableMace() {}

    public static ItemStack make(JavaPlugin plugin) {
        ItemStack mace = new ItemStack(Material.MACE);
        mace.editMeta(meta -> meta.getPersistentDataContainer()
                .set(ItemKeys.enchantableMace(plugin), PersistentDataType.BYTE, (byte) 1));
        return mace;
    }

    /** True for a mace that carries the enchantable tag. */
    public static boolean isTagged(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(ItemKeys.enchantableMace(plugin), PersistentDataType.BYTE);
    }

    /** True for a mace that is NOT tagged, i.e. one that must never hold enchantments. */
    public static boolean isRestrictedMace(JavaPlugin plugin, ItemStack item) {
        return item != null && item.getType() == Material.MACE && !isTagged(plugin, item);
    }
}
