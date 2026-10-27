package net.rose.elementSMPRefined.items.recipes;

import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.items.builder.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Forged Upgrader. Not craftable - it only comes from the Reforge Altar.
 * Replaces the old Advanced Reroller: instead of rolling a random advanced
 * element it reforges the player's basic element into its advanced form
 * (Fire -> Lava, Earth -> Metal, Water -> Frost, Air -> Storm).
 */
public final class ForgedUpgraderItem {
    private ForgedUpgraderItem() {}

    public static ItemStack make(JavaPlugin plugin) {
        return ItemBuilder.of(Material.ENDER_EYE)
                .name("&6&lForged Upgrader")
                .lore(
                        "&7Forges your basic element",
                        "&7into its advanced stage")
                .data(ItemKeys.forgedUpgrader(plugin), (byte) 1)
                .build();
    }
}
