package net.rose.elementSMPRefined.weapon.disc;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.Objects;

/**
 * The two stages of the one throwing disc. Both are plain tools underneath (so the vanilla
 * durability, the netherite upgrade smithing and the item entity behaviour all come for free) that
 * wear a resource-pack {@code item_model}.
 */
public enum DiscTier {
    DIAMOND(
            Material.DIAMOND_HOE,
            "diamond_throwing_disc",
            "Diamond Throwing Disc",
            NamedTextColor.AQUA,
            6.0),
    NETHERITE(
            Material.NETHERITE_HOE,
            "netherite_throwing_disc",
            "Netherite Throwing Disc",
            NamedTextColor.DARK_PURPLE,
            7.0);

    private final Material material;
    private final NamespacedKey model;
    private final String displayName;
    private final NamedTextColor colour;
    private final double damage;

    DiscTier(
            Material material,
            String modelName,
            String displayName,
            NamedTextColor colour,
            double damage) {
        this.material = material;
        this.model = Objects.requireNonNull(NamespacedKey.fromString("elementsmp:" + modelName));
        this.displayName = displayName;
        this.colour = colour;
        this.damage = damage;
    }

    public Material material() {
        return material;
    }

    public NamespacedKey model() {
        return model;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor colour() {
        return colour;
    }

    /** Damage per hit, in half hearts. */
    public double damage() {
        return damage;
    }

    public static DiscTier of(Material material) {
        for (DiscTier tier : values()) {
            if (tier.material == material) return tier;
        }
        return null;
    }
}
