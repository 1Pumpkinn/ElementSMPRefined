package net.rose.elementSMPRefined.items.recipes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    /** Reddish pink used for the Forged Upgrader's name. */
    public static final TextColor COLOR = TextColor.color(0xAD1457);

    private ForgedUpgraderItem() {}

    public static ItemStack make(JavaPlugin plugin) {
        return ItemBuilder.of(Material.ENDER_EYE)
                .name(Component.text("Forged Upgrader", COLOR))
                .lore(
                        "&7Forges your basic element",
                        "&7into its advanced stage")
                .data(ItemKeys.forgedUpgrader(plugin), (byte) 1)
                .build();
    }
}