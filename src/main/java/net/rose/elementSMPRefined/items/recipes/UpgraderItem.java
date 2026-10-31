package net.rose.elementSMPRefined.items.recipes;

import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.items.builder.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * The Upgrader items (levels 1 and 2). Both share one recipe shape:
 * <pre>
 *   X A X
 *   A Y A     X = corner block, A = amethyst shard, Y = centre item
 *   X A X
 * </pre>
 * Only the names, lore and the X / Y materials differ per level.
 */
public final class UpgraderItem {
    private UpgraderItem() {}

    private record Tier(String key, String name, String lore, Material corner, Material center) {}

    private static final Map<Integer, Tier> TIERS = Map.of(
            1, new Tier("upgrader_1", "&aUpgrader I", "Unlocks Ability 1",
                    Material.GOLD_BLOCK, Material.ENDER_EYE),
            2, new Tier("upgrader_2", "&bUpgrader II", "Unlocks Ability 2 and Passive 2",
                    Material.DIAMOND_BLOCK, Material.TOTEM_OF_UNDYING));

    /** @return the upgrader for {@code level}, or null if there is no upgrader for that level */
    public static ItemStack make(JavaPlugin plugin, int level) {
        Tier tier = TIERS.get(level);
        if (tier == null) return null;

        return ItemBuilder.of(Material.AMETHYST_SHARD)
                .name(tier.name())
                .itemModel(NamespacedKey.fromString("elementsmp:" + tier.key()))
                .lore(tier.lore())
                .data(ItemKeys.upgraderLevel(plugin), level)
                .build();
    }

    public static void registerRecipes(JavaPlugin plugin) {
        TIERS.forEach((level, tier) -> {
            try {
                NamespacedKey key = new NamespacedKey(plugin, tier.key());
                plugin.getServer().removeRecipe(key);

                ShapedRecipe recipe = new ShapedRecipe(key, make(plugin, level));
                recipe.shape("XAX", "AYA", "XAX");
                recipe.setIngredient('X', tier.corner());
                recipe.setIngredient('A', Material.AMETHYST_SHARD);
                recipe.setIngredient('Y', tier.center());

                plugin.getServer().addRecipe(recipe);
            } catch (Exception e) {
                plugin.getLogger().severe("Error registering " + tier.key() + " recipe: " + e.getMessage());
            }
        });
    }
}
