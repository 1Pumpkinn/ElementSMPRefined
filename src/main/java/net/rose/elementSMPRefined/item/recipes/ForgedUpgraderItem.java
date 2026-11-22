package net.rose.elementSMPRefined.item.recipes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.rose.elementSMPRefined.item.ItemKeys;
import net.rose.elementSMPRefined.item.builder.ItemBuilder;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Forged Upgrader. Not craftable - it only comes from the Reforge Altar.
 * Replaces the old Advanced Reroller: instead of rolling a random advanced
 * element it reforges the player's basic element into its advanced form
 * (Fire -> Lava, Earth -> Metal, Water -> Ice, Air -> Storm).
 */
public final class ForgedUpgraderItem {
    public static final TextColor COLOR = TextColor.color(0xAD1457);
    public static final TextColor COLOR_LIGHT = TextColor.color(0xF06292);
    public static final TextColor COLOR_SOFT = TextColor.color(0xF8BBD0);

    private ForgedUpgraderItem() {}

    public static ItemStack make(JavaPlugin plugin) {
        return ItemBuilder.of(Material.ENDER_EYE)
                .name(Component.text("Forged Upgrader", COLOR))
                .itemModel(NamespacedKey.fromString("elementsmp:forged_upgrader"))
                .lore("&7Forges your basic element", "&7into its advanced stage")
                .data(ItemKeys.forgedUpgrader(plugin), (byte) 1)
                .build();
    }
}
