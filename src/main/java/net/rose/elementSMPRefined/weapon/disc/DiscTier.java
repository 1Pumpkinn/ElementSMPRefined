package net.rose.elementSMPRefined.weapon.disc;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.Objects;

/**
 * The two stages of the one throwing disc. Both are plain tools underneath (so the vanilla
 * durability, the netherite upgrade smithing and the item entity behaviour all come for free) that
 * wear a resource-pack {@code item_model}.
 * <p>
 * The base items are swords, not hoes: a hoe tills (and plays the tilling sound on) grass and dirt
 * when right-clicked, and holding a netherite hoe grants the "Serious Dedication" advancement.
 * Swords have no right-click block behaviour and no advancement. The hoes are only kept as
 * {@code legacyMaterial} so discs made before the change are still recognised and get converted.
 */
public enum DiscTier {
    DIAMOND(
            Material.DIAMOND_SWORD,
            Material.DIAMOND_HOE,
            "diamond_throwing_disc",
            "Diamond Throwing Disc",
            NamedTextColor.AQUA,
            6.0),
    NETHERITE(
            Material.NETHERITE_SWORD,
            Material.NETHERITE_HOE,
            "netherite_throwing_disc",
            "Netherite Throwing Disc",
            NamedTextColor.DARK_PURPLE,
            7.0);

    private final Material material;
    private final Material legacyMaterial;
    private final NamespacedKey model;
    private final String displayName;
    private final NamedTextColor colour;
    private final double damage;

    DiscTier(
            Material material,
            Material legacyMaterial,
            String modelName,
            String displayName,
            NamedTextColor colour,
            double damage) {
        this.material = material;
        this.legacyMaterial = legacyMaterial;
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

    /** True if the material is the old hoe base that discs used before they became swords. */
    public boolean isLegacy(Material other) {
        return other == legacyMaterial;
    }

    /** The tier a material belongs to (current or legacy hoe base), or null. */
    public static DiscTier of(Material material) {
        for (DiscTier tier : values()) {
            if (tier.material == material || tier.legacyMaterial == material) return tier;
        }
        return null;
    }
}
