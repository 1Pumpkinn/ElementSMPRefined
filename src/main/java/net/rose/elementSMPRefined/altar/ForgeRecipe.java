package net.rose.elementSMPRefined.altar;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

/**
 * One Reforge Altar recipe, tied to the basic element it reforges. The altar
 * asks each player for the recipe of THEIR element (Fire -> Lava recipe, Water
 * -> Frost recipe, Air -> Storm recipe, Earth -> Metal recipe), so two players
 * at the same altar see - and must hand in - different ingredients. The
 * Forged Upgrader it hands out then does the matching reforge
 * (see {@link #forBase(ElementType)}).
 */
public enum ForgeRecipe {
    FROST(ElementType.WATER, ElementType.FROST,
            new Ingredient(Material.BLUE_ICE, 128, "Blue Ice"),
            new Ingredient(Material.POWDER_SNOW_BUCKET, 4, "Powdered Snow Bucket")),
    LAVA(ElementType.FIRE, ElementType.LAVA,
            new Ingredient(Material.MAGMA_BLOCK, 128, "Magma Block"),
            new Ingredient(Material.MAGMA_CREAM, 64, "Magma Cream")),
    STORM(ElementType.AIR, ElementType.STORM,
            new Ingredient(Material.BREEZE_ROD, 64, "Breeze Rod"),
            new Ingredient(Material.COPPER_BLOCK, 128, "Copper Block")),
    METAL(ElementType.EARTH, ElementType.METAL,
            new Ingredient(Material.IRON_BLOCK, 64, "Iron Block"),
            new Ingredient(Material.HEAVY_CORE, 1, "Heavy Core"));

    /** One required stack: {@code amount} of plain (unrenamed) {@code material}. */
    public record Ingredient(Material material, int amount, String name) {
        public String label() {
            return amount + "x " + name;
        }
    }

    private final ElementType base;
    private final ElementType target;
    private final List<Ingredient> ingredients;

    ForgeRecipe(ElementType base, ElementType target, Ingredient first, Ingredient second) {
        this.base = base;
        this.target = target;
        this.ingredients = List.of(first, second);
    }

    /** The basic element a Forged Upgrader turns into {@link #target()}. */
    public ElementType base() { return base; }

    public ElementType target() { return target; }

    public List<Ingredient> ingredients() { return ingredients; }

    /** The recipe that produces {@code element} (e.g. LAVA for a Lava player); empty for basic/mythic elements. */
    public static Optional<ForgeRecipe> forTarget(ElementType element) {
        for (ForgeRecipe recipe : values()) {
            if (recipe.target == element) return Optional.of(recipe);
        }
        return Optional.empty();
    }

    /** What a Forged Upgrader does to a player holding {@code element}; empty if it can't be forged. */
    public static Optional<ForgeRecipe> forBase(ElementType element) {
        for (ForgeRecipe recipe : values()) {
            if (recipe.base == element) return Optional.of(recipe);
        }
        return Optional.empty();
    }
}
